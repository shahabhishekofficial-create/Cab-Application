package com.caboperations.driver.data

import androidx.room.withTransaction
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.time.Instant
import java.util.UUID

class FuelLocalRepository(private val context: android.content.Context) {
    private val db get() = CabDatabase.get(context)

    suspend fun queueFuel(
        sessionId: String,
        driverId: String,
        vehicleId: String,
        fuelType: String,
        odometer: Double,
        quantity: Double,
        unit: String,
        rate: Double,
        amount: Double,
        paymentMethod: String? = null,
        receiptFileId: String? = null,
        latitude: Double? = null,
        longitude: Double? = null,
        gpsAccuracyM: Double? = null,
        recordedAt: String = Instant.now().toString(),
        notes: String? = null
    ): String {
        require(quantity > 0) { "Fuel quantity must be positive" }
        require(rate >= 0 && amount >= 0 && odometer >= 0) { "Invalid fuel values" }

        val id = UUID.randomUUID().toString()
        val payload = buildJsonObject {
            put("clientTransactionId", id)
            put("sessionId", sessionId)
            put("driverId", driverId)
            put("vehicleId", vehicleId)
            put("fuelType", fuelType)
            put("odometer", odometer)
            put("quantity", quantity)
            put("unit", unit)
            put("rate", rate)
            put("amount", amount)
            paymentMethod?.let { put("paymentMethod", it) }
            receiptFileId?.let { put("receiptFileId", it) }
            latitude?.let { put("latitude", it) }
            longitude?.let { put("longitude", it) }
            gpsAccuracyM?.let { put("gpsAccuracyM", it) }
            put("recordedAt", recordedAt)
            notes?.let { put("notes", it) }
        }.toString()

        db.withTransaction {
            db.pendingTransactionDao().insert(PendingTransaction(id, "FUEL", payload, System.currentTimeMillis()))
            db.localFuelDao().insert(LocalFuel(id, sessionId, odometer, quantity, rate, amount, fuelType, false))
        }
        return id
    }
}
