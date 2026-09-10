package com.caboperations.driver.data

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.caboperations.driver.network.ApiClient
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/** Durable offline sync. Each client transaction is sent independently so one bad item
 * cannot block the rest of the queue. Server-side clientTransactionId makes retries safe. */
class SyncWorker(appContext: Context, params: WorkerParameters) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        val dao = CabDatabase.get(applicationContext).pendingTransactionDao()
        val pending = dao.pending()
        if (pending.isEmpty()) return Result.success()

        val baseUrl = inputData.getString(KEY_BASE_URL)
            ?: return Result.failure()
        val api = ApiClient(baseUrl)
        var retry = false

        for (item in pending) {
            val json = runCatching { Json.parseToJsonElement(item.payloadJson).jsonObject }.getOrNull()
            val driverId = json?.get("driverId")?.jsonPrimitive?.content
            val vehicleId = json?.get("vehicleId")?.jsonPrimitive?.content
            val path = when (item.type) {
                TYPE_SESSION_START -> "/v1/sessions"
                TYPE_SESSION_CLOSE -> {
                    val sessionId = json?.get("sessionId")?.jsonPrimitive?.content
                    if (sessionId.isNullOrBlank()) {
                        dao.markFailed(item.clientTransactionId, "MISSING_SESSION_ID")
                        continue
                    }
                    "/v1/sessions/$sessionId/close"
                }
                TYPE_TRIP -> "/v1/trips"
                TYPE_FUEL -> "/v1/fuel"
                TYPE_EXPENSE -> "/v1/expenses"
                else -> {
                    dao.markFailed(item.clientTransactionId, "UNSUPPORTED_TRANSACTION_TYPE")
                    continue
                }
            }

            val result = api.post(path, item.payloadJson, driverId, vehicleId)
            if (result.success) {
                dao.markSynced(item.clientTransactionId)
            } else {
                dao.markFailed(item.clientTransactionId, result.error ?: "SYNC_FAILED")
                if (result.retryable) retry = true
            }
        }
        return if (retry) Result.retry() else Result.success()
    }

    companion object {
        const val KEY_BASE_URL = "api_base_url"
        const val TYPE_SESSION_START = "SESSION_START"
        const val TYPE_SESSION_CLOSE = "SESSION_CLOSE"
        const val TYPE_TRIP = "TRIP"
        const val TYPE_FUEL = "FUEL"
        const val TYPE_EXPENSE = "EXPENSE"
    }
}
