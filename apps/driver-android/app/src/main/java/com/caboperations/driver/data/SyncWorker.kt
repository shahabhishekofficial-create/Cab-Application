package com.caboperations.driver.data

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.caboperations.driver.BuildConfig
import com.caboperations.driver.auth.AuthRepository
import com.caboperations.driver.network.ApiClient
import com.caboperations.driver.sync.SyncPolicy
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File

/** Durable offline sync. Files are uploaded before transactions that reference them. */
class SyncWorker(appContext: Context, params: WorkerParameters) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        val dao = CabDatabase.get(applicationContext).pendingTransactionDao()
        val pending = dao.pending().take(SyncPolicy.MAX_BATCH_SIZE)
        if (pending.isEmpty()) return Result.success()

        val baseUrl = inputData.getString(KEY_BASE_URL) ?: BuildConfig.API_BASE_URL
        val auth = AuthRepository(applicationContext)
        val tokenResult = auth.refreshIfNeeded()
        if (tokenResult.isFailure) return Result.retry()
        var token = tokenResult.getOrNull()?.accessToken ?: return Result.retry()
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
            var authRetried = false

            fun postPath(): String? = when (item.type) {
                TYPE_SESSION_START -> "/v1/sessions"
                TYPE_SESSION_CLOSE -> json["sessionId"]?.jsonPrimitive?.content?.let { "/v1/sessions/$it/close" }
                TYPE_TRIP -> "/v1/trips"
                TYPE_FUEL -> "/v1/fuel"
                TYPE_EXPENSE -> "/v1/expenses"
                else -> null
            }

            fun upload(): ApiClient.Result? {
                val filePath = json["localFilePath"]?.jsonPrimitive?.content
                val fileId = json["fileId"]?.jsonPrimitive?.content
                val objectPath = json["objectPath"]?.jsonPrimitive?.content
                val mimeType = json["mimeType"]?.jsonPrimitive?.content ?: "image/jpeg"
                val capturedAt = json["capturedAt"]?.jsonPrimitive?.content
                if (filePath.isNullOrBlank() || fileId.isNullOrBlank() || objectPath.isNullOrBlank()) return null
                val file = File(filePath)
                if (!file.exists()) return null
                return api.uploadFile("/v1/files", fileId, objectPath, mimeType, file.readBytes(), capturedAt)
            }

            val result = if (item.type == TYPE_FILE_UPLOAD) {
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
                upload()!!
            } else {
                val path = postPath()
                if (path == null) {
                    dao.markFailed(item.clientTransactionId, "UNSUPPORTED_TRANSACTION_TYPE")
                    continue
                }
                api.post(path, item.payloadJson, driverId, vehicleId)
            }

            if (result.success) {
                dao.markSynced(item.clientTransactionId)
                continue
            }

            if (result.authExpired && !authRetried) {
                val refreshed = auth.refreshIfNeeded(force = true)
                val refreshedToken = refreshed.getOrNull()?.accessToken
                if (refreshedToken != null) {
                    token = refreshedToken
                    api = ApiClient(baseUrl, token)
                    authRetried = true
                    val retryResult = if (item.type == TYPE_FILE_UPLOAD) upload() else postPath()?.let { api.post(it, item.payloadJson, driverId, vehicleId) }
                    if (retryResult?.success == true) {
                        dao.markSynced(item.clientTransactionId)
                        continue
                    }
                    if (retryResult?.retryable == true) retry = true
                    else dao.markFailed(item.clientTransactionId, retryResult?.error ?: "AUTH_REFRESH_FAILED")
                } else {
                    retry = true
                }
            } else if (result.retryable) {
                // Keep the item pending. A transient failure must not become a permanent
                // local failure merely because this worker invocation ended.
                retry = true
            } else {
                dao.markFailed(item.clientTransactionId, result.error ?: "SYNC_FAILED")
            }

            if (!result.success && (item.type == TYPE_SESSION_START || item.type == TYPE_SESSION_CLOSE)) break
        }
        return if (retry) Result.retry() else Result.success()
    }

    companion object {
        const val KEY_BASE_URL = "api_base_url"
        const val TYPE_FILE_UPLOAD = "FILE_UPLOAD"
        const val TYPE_SESSION_START = "SESSION_START"
        const val TYPE_SESSION_CLOSE = "SESSION_CLOSE"
        const val TYPE_TRIP = "TRIP"
        const val TYPE_FUEL = "FUEL"
        const val TYPE_EXPENSE = "EXPENSE"
    }
}
