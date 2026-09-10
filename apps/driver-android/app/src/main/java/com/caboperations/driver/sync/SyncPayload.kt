package com.caboperations.driver.sync

import java.util.UUID

sealed class SyncPayload(open val clientTransactionId: String, open val type: String) {
    data class Trip(
        override val clientTransactionId: String = UUID.randomUUID().toString(),
        val sessionId: String,
        val startOdometer: Double,
        val endOdometer: Double?,
        val grossFare: Double,
        val status: String,
    ) : SyncPayload(clientTransactionId, "TRIP")

    data class Fuel(
        override val clientTransactionId: String = UUID.randomUUID().toString(),
        val sessionId: String,
        val odometer: Double,
        val quantity: Double,
        val rate: Double,
        val amount: Double,
        val fuelType: String,
    ) : SyncPayload(clientTransactionId, "FUEL")

    data class Expense(
        override val clientTransactionId: String = UUID.randomUUID().toString(),
        val sessionId: String,
        val amount: Double,
        val categoryId: String?,
    ) : SyncPayload(clientTransactionId, "EXPENSE")
}
