package com.caboperations.driver.data

import android.content.Context
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.time.Instant
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
        val now = Instant.now().toString()
        val payload = buildJsonObject {
            put("clientTransactionId", transactionId)
            put("sessionId", sessionId)
            put("driverId", driverId)
            put("vehicleId", vehicleId)
            deviceId?.let { put("deviceId", it) }
            put("startedAt", now)
            put("startOdometer", startOdometer)
            startLat?.let { put("startLat", it) }
            startLng?.let { put("startLng", it) }
            startAccuracyM?.let { put("startAccuracyM", it) }
            startGpsAt?.let { put("startGpsAt", it) }
            startOdometerFileId?.let { put("startOdometerFileId", it) }
        }.toString()

        db.runInTransaction {
            // Local source of truth is created before any network attempt.
            // Room's synchronous transaction keeps the session row and pending queue consistent.
            db.localSessionDao().insert(
                LocalSession(sessionId, driverId, vehicleId, "OPEN", startOdometer)
            )
        }
        db.pendingTransactionDao().insert(
            PendingTransaction(transactionId, "SESSION_START", payload, System.currentTimeMillis())
        )
        return sessionId
    }
}
