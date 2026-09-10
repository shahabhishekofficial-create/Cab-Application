package com.caboperations.driver.data

import androidx.room.withTransaction
import android.content.Context
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.time.Instant
import java.util.UUID

class SessionCloseLocalRepository(private val context: Context) {
    private val db get() = CabDatabase.get(context)

    suspend fun queueCloseSession(
        sessionId: String,
        driverId: String,
        vehicleId: String,
        closeOdometer: Double,
        closeLat: Double?,
        closeLng: Double?,
        closeAccuracyM: Float?,
        closeGpsAt: String?,
        closeOdometerFileId: String? = null,
        reportedTripCount: Int,
        reportedIncome: Double,
        notes: String? = null
    ) {
        require(closeOdometer >= 0) { "Closing odometer must be non-negative" }
        require(reportedTripCount >= 0) { "Reported trip count must be non-negative" }
        require(reportedIncome >= 0) { "Reported income must be non-negative" }

        val session = db.localSessionDao().find(sessionId)
            ?: throw IllegalStateException("SESSION_NOT_FOUND")
        require(session.driverId == driverId) { "SESSION_DRIVER_MISMATCH" }
        require(session.vehicleId == vehicleId) { "SESSION_VEHICLE_MISMATCH" }
        require(session.status == "OPEN") { "SESSION_NOT_OPEN" }
        require(closeOdometer >= session.startOdometer) { "INVALID_CLOSE_ODOMETER" }

        val transactionId = UUID.randomUUID().toString()
        val closedAt = Instant.now().toString()
        val payload = buildJsonObject {
            put("clientTransactionId", transactionId)
            put("sessionId", sessionId)
            put("driverId", driverId)
            put("vehicleId", vehicleId)
            put("closedAt", closedAt)
            put("closeOdometer", closeOdometer)
            closeLat?.let { put("closeLat", it) }
            closeLng?.let { put("closeLng", it) }
            closeAccuracyM?.let { put("closeAccuracyM", it) }
            closeGpsAt?.let { put("closeGpsAt", it) }
            closeOdometerFileId?.let { put("closeOdometerFileId", it) }
            put("reportedTripCount", reportedTripCount)
            put("reportedIncome", reportedIncome)
            notes?.let { put("notes", it) }
        }.toString()

        db.withTransaction {
            db.localSessionDao().markClosed(sessionId, closeOdometer)
            db.pendingTransactionDao().insert(
                PendingTransaction(transactionId, "SESSION_CLOSE", payload, System.currentTimeMillis())
            )
        }
    }
}
