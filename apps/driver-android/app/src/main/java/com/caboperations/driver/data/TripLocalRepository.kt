package com.caboperations.driver.data

import androidx.room.withTransaction
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.time.Instant
import java.util.UUID

class TripLocalRepository(private val context: android.content.Context) {
    private val db get() = CabDatabase.get(context)

    suspend fun queueTrip(
        sessionId: String,
        driverId: String,
        vehicleId: String,
        startOdometer: Double,
        endOdometer: Double?,
        grossFare: Double,
        status: String,
        platformId: String? = null,
        pickup: String? = null,
        dropoff: String? = null,
        paymentMethod: String? = null,
        additionalCharges: Double = 0.0,
        startedAt: String = Instant.now().toString(),
        endedAt: String? = null,
        notes: String? = null
    ): String {
        require(startOdometer >= 0) { "Start odometer cannot be negative" }
        require(endOdometer == null || endOdometer >= startOdometer) { "End odometer cannot be less than start odometer" }
        require(grossFare >= 0) { "Gross fare cannot be negative" }
        require(additionalCharges >= 0) { "Additional charges cannot be negative" }

        val id = UUID.randomUUID().toString()
        val payload = buildJsonObject {
            put("clientTransactionId", id); put("sessionId", sessionId); put("driverId", driverId); put("vehicleId", vehicleId)
            platformId?.let { put("platformId", it) }; put("startedAt", startedAt); endedAt?.let { put("endedAt", it) }
            pickup?.let { put("pickup", it) }; dropoff?.let { put("dropoff", it) }; put("startOdometer", startOdometer)
            endOdometer?.let { put("endOdometer", it) }; put("grossFare", grossFare); paymentMethod?.let { put("paymentMethod", it) }
            put("additionalCharges", additionalCharges); put("status", status); notes?.let { put("notes", it) }
        }.toString()

        db.withTransaction {
            OdometerGuard.requireAtLeast(db, sessionId, startOdometer, "Trip start odometer")
            endOdometer?.let { OdometerGuard.requireAtLeast(db, sessionId, it, "Trip end odometer") }
            db.pendingTransactionDao().insert(PendingTransaction(id, "TRIP", payload, System.currentTimeMillis()))
            db.localTripDao().insert(LocalTrip(id, sessionId, startOdometer, endOdometer, grossFare, status, false))
        }
        return id
    }
}
