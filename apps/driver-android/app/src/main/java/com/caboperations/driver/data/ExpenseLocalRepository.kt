package com.caboperations.driver.data

import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.time.Instant
import java.util.UUID

class ExpenseLocalRepository(private val context: android.content.Context) {
    private val db get() = CabDatabase.get(context)

    suspend fun queueExpense(
        sessionId: String,
        driverId: String,
        vehicleId: String,
        amount: Double,
        categoryId: String? = null,
        paymentMethod: String? = null,
        proofFileId: String? = null,
        odometer: Double? = null,
        latitude: Double? = null,
        longitude: Double? = null,
        gpsAccuracyM: Double? = null,
        recordedAt: String = Instant.now().toString(),
        notes: String? = null
    ): String {
        require(amount >= 0) { "Expense amount cannot be negative" }

        val id = UUID.randomUUID().toString()
        val payload = buildJsonObject {
            put("clientTransactionId", id)
            put("sessionId", sessionId)
            put("driverId", driverId)
            put("vehicleId", vehicleId)
            categoryId?.let { put("categoryId", it) }
            put("amount", amount)
            paymentMethod?.let { put("paymentMethod", it) }
            proofFileId?.let { put("proofFileId", it) }
            odometer?.let { put("odometer", it) }
            latitude?.let { put("latitude", it) }
            longitude?.let { put("longitude", it) }
            gpsAccuracyM?.let { put("gpsAccuracyM", it) }
            put("recordedAt", recordedAt)
            notes?.let { put("notes", it) }
        }.toString()

        db.pendingTransactionDao().insert(
            PendingTransaction(id, "EXPENSE", payload, System.currentTimeMillis())
        )
        db.localExpenseDao().insert(
            LocalExpense(id, sessionId, amount, categoryId, false)
        )
        return id
    }
}
