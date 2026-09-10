package com.caboperations.driver.data

import android.content.Context
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.util.UUID

class SessionLocalRepository(private val context: Context) {
    private val db get() = CabDatabase.get(context)

    suspend fun queueStartSession(
        driverId: String,
        vehicleId: String,
        deviceId: String?,
        startOdometer: Double,
        startLat: Double?,
        startLng: Double?,
        startAccuracyM: Float?,
        startGpsAt: String?,
        startOdometerFileId: String? = null
    ): String {
        val transactionId = UUID.randomUUID().toString()
        val sessionId = UUID.randomUUID().toString()
        val payload = buildJsonObject {
            put("clientTransactionId", transactionId)
            put("driverId", driverId)
            put("vehicleId", vehicleId)
            deviceId?.let { put("deviceId", it) }
            put("startedAt", java.time.Instant.now().toString())
            put("startOdometer", startOdometer)
            startLat?.let { put("startLat", it) }
            startLng?.let { put("startLng", it) }
            startAccuracyM?.let { put("startAccuracyM", it) }
            startGpsAt?.let { put("startGpsAt", it) }
            startOdometerFileId?.let { put("startOdometerFileId", it) }
        }.toString()

        db.runInTransaction {
            // Room's synchronous transaction is intentionally kept small; queue insertion
            // is the source of truth before any network attempt.
        }
        db.pendingTransactionDao().insert(
            PendingTransaction(transactionId, "SESSION_START", payload, System.currentTimeMillis())
        )
        return sessionId
    }
}
