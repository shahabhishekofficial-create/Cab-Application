package com.caboperations.driver.data

import android.content.Context
import com.caboperations.driver.BuildConfig
import com.caboperations.driver.auth.AuthRepository
import com.caboperations.driver.network.ApiClient
import com.caboperations.driver.sync.SyncPolicy
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import java.io.File

/** Single durable sync path used both immediately and by WorkManager retry. */
object SyncEngine {
    private val mutex = Mutex()

    suspend fun run(context: Context, baseUrl: String = BuildConfig.API_BASE_URL): Boolean = mutex.withLock {
        val dao = CabDatabase.get(context).pendingTransactionDao()
        val pending = dao.pending().sortedWith(compareBy { priority(it.type) }).take(SyncPolicy.MAX_BATCH_SIZE)
        if (pending.isEmpty()) return@withLock true

        val auth = AuthRepository(context)
        val currentSession = auth.session()
        val tokenResult = auth.refreshIfNeeded()
        // A transient refresh failure must not suppress the actual API sync. If a
        // cached access token exists, use it and let the API decide whether it is
        // still valid; a 401 below will trigger the normal forced-refresh path.
        if (tokenResult.isFailure && currentSession == null) return@withLock false
        var token = tokenResult.getOrNull()?.accessToken ?: currentSession?.accessToken ?: return@withLock false
        var api = ApiClient(baseUrl, token)
        var retry = false

        for (item in pending) {
            val json = runCatching { Json.parseToJsonElement(item.payloadJson).jsonObject }.getOrNull()
            if (json == null) {
                dao.markFailed(item.clientTransactionId, "INVALID_LOCAL_PAYLOAD")
                continue
            }
            val driverId = json["driverId"]?.jsonPrimitive?.content
            val vehicleId = json["vehicleId"]?.jsonPrimitive?.content
            var result: ApiClient.Result

            fun path(): String? = when (item.type) {
                TYPE_SESSION_START -> "/v1/sessions"
                TYPE_SESSION_CLOSE -> json["sessionId"]?.jsonPrimitive?.content?.let { "/v1/sessions/$it/close" }
                TYPE_TRIP_START -> "/v1/trips/start"
                TYPE_TRIP_END -> "/v1/trips/end"
                TYPE_TRIP -> "/v1/trips"
                TYPE_FUEL -> "/v1/fuel"
                TYPE_EXPENSE -> "/v1/expenses"
                else -> null
            }

            fun objectSessionId(pathValue: String): String? {
                val parts = pathValue.split('/')
                return if (parts.size >= 3 && parts[0] == "sessions") parts[1] else null
            }

            fun attachOdometerFile(): ApiClient.Result {
                val objectPath = json["objectPath"]?.jsonPrimitive?.content ?: return ApiClient.Result(false, false, "INVALID_FILE_UPLOAD_PAYLOAD")
                val sessionId = json["sessionId"]?.jsonPrimitive?.content ?: objectSessionId(objectPath) ?: return ApiClient.Result(false, false, "SESSION_ID_MISSING")
                val fileId = json["fileId"]?.jsonPrimitive?.content ?: return ApiClient.Result(false, false, "INVALID_FILE_UPLOAD_PAYLOAD")
                val body = buildJsonObject { put("fileId", fileId); put("objectPath", objectPath) }.toString()
                return api.post("/v1/sessions/$sessionId/odometer-file", body, driverId, vehicleId)
            }

            fun upload(): ApiClient.Result? {
                val filePath = json["localFilePath"]?.jsonPrimitive?.content
                val fileId = json["fileId"]?.jsonPrimitive?.content
                val objectPath = json["objectPath"]?.jsonPrimitive?.content
                if (filePath.isNullOrBlank() || fileId.isNullOrBlank() || objectPath.isNullOrBlank()) return null
                val file = File(filePath)
                if (!file.exists()) return null
                val mime = json["mimeType"]?.jsonPrimitive?.content ?: "image/jpeg"
                val captured = json["capturedAt"]?.jsonPrimitive?.content
                return api.uploadFile("/v1/files", fileId, objectPath, mime, file.readBytes(), captured)
            }

            result = if (item.type == TYPE_FILE_UPLOAD) {
                val filePath = json["localFilePath"]?.jsonPrimitive?.content
                val fileId = json["fileId"]?.jsonPrimitive?.content
                val objectPath = json["objectPath"]?.jsonPrimitive?.content
                if (filePath.isNullOrBlank() || fileId.isNullOrBlank() || objectPath.isNullOrBlank()) {
                    dao.markFailed(item.clientTransactionId, "INVALID_FILE_UPLOAD_PAYLOAD")
                    continue
                }
                if (!File(filePath).exists()) {
                    dao.markFailed(item.clientTransactionId, "LOCAL_FILE_MISSING")
                    continue
                }
                val uploaded = upload() ?: ApiClient.Result(false, true, "FILE_UPLOAD_FAILED")
                if (uploaded.success) attachOdometerFile() else uploaded
            } else {
                val p = path()
                if (p == null) {
                    dao.markFailed(item.clientTransactionId, "UNSUPPORTED_TRANSACTION_TYPE")
                    continue
                }
                // Older app builds queued startOdometerFileId on SESSION_START,
                // but the server session row has a foreign key to files and the
                // upload is a later transaction. Strip the legacy field during
                // sync so existing queued sessions can migrate safely.
                val body = if (item.type == TYPE_SESSION_START && json.containsKey("startOdometerFileId")) {
                    buildJsonObject {
                        json.forEach { (key, value) -> if (key != "startOdometerFileId") put(key, value) }
                    }.toString()
                } else item.payloadJson
                api.post(p, body, driverId, vehicleId)
            }

            if (result.success) {
                dao.markSynced(item.clientTransactionId)
                continue
            }

            if (result.authExpired) {
                val refreshed = auth.refreshIfNeeded(force = true)
                val newToken = refreshed.getOrNull()?.accessToken
                if (newToken == null) {
                    dao.markFailed(item.clientTransactionId, "AUTH_REFRESH_FAILED")
                    retry = dao.find(item.clientTransactionId)?.attempts?.let { it < SyncPolicy.MAX_RETRY_ATTEMPTS } ?: false
                    break
                }
                token = newToken
                api = ApiClient(baseUrl, token)
                val p = path()
                val body = if (item.type == TYPE_SESSION_START && json.containsKey("startOdometerFileId")) {
                    buildJsonObject {
                        json.forEach { (key, value) -> if (key != "startOdometerFileId") put(key, value) }
                    }.toString()
                } else item.payloadJson
                val retryResult = if (item.type == TYPE_FILE_UPLOAD) {
                    val uploaded = upload()
                    if (uploaded?.success == true) attachOdometerFile() else uploaded
                } else p?.let { api.post(it, body, driverId, vehicleId) }
                if (retryResult?.success == true) {
                    dao.markSynced(item.clientTransactionId)
                    continue
                }
                val error = retryResult?.error ?: "AUTH_REFRESH_FAILED"
                dao.markFailed(item.clientTransactionId, error)
                retry = dao.find(item.clientTransactionId)?.attempts?.let { it < SyncPolicy.MAX_RETRY_ATTEMPTS } ?: false
            } else if (result.retryable) {
                // Count transient failures too. Previously these never incremented
                // attempts, which allowed one broken transaction to hammer the API
                // indefinitely through immediate sync + WorkManager.
                dao.markFailed(item.clientTransactionId, result.error ?: "SYNC_RETRYABLE_FAILURE")
                retry = dao.find(item.clientTransactionId)?.attempts?.let { it < SyncPolicy.MAX_RETRY_ATTEMPTS } ?: false
            } else {
                dao.markFailed(item.clientTransactionId, result.error ?: "SYNC_FAILED")
            }

            if (item.type in setOf(TYPE_SESSION_START, TYPE_TRIP_START, TYPE_TRIP_END)) break
        }
        !retry
    }

    private fun priority(type: String) = when (type) {
        TYPE_SESSION_START -> 0
        TYPE_TRIP_START -> 10
        TYPE_TRIP_END -> 11
        TYPE_TRIP -> 12
        TYPE_FUEL, TYPE_EXPENSE -> 12
        TYPE_SESSION_CLOSE -> 20
        TYPE_FILE_UPLOAD -> 30
        else -> 40
    }

    const val TYPE_FILE_UPLOAD = "FILE_UPLOAD"
    const val TYPE_SESSION_START = "SESSION_START"
    const val TYPE_SESSION_CLOSE = "SESSION_CLOSE"
    const val TYPE_TRIP_START = "TRIP_START"
    const val TYPE_TRIP_END = "TRIP_END"
    const val TYPE_TRIP = "TRIP"
    const val TYPE_FUEL = "FUEL"
    const val TYPE_EXPENSE = "EXPENSE"
}
