package com.caboperations.driver.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "pending_transactions")
data class PendingTransaction(
    @PrimaryKey val clientTransactionId: String,
    val type: String,
    val payloadJson: String,
    val createdAt: Long,
    val attempts: Int = 0,
    val lastError: String? = null,
    val synced: Boolean = false
)

@Entity(tableName = "sessions")
data class LocalSession(
    @PrimaryKey val sessionId: String,
    val driverId: String,
    val vehicleId: String,
    val status: String,
    val startOdometer: Double,
    val closeOdometer: Double? = null
)

@Entity(tableName = "trips")
data class LocalTrip(
    @PrimaryKey val clientTransactionId: String,
    val sessionId: String,
    val startOdometer: Double,
    val endOdometer: Double?,
    val grossFare: Double,
    val status: String,
    val latitude: Double,
    val longitude: Double,
    val gpsAccuracyM: Double,
    val gpsAt: String,
    val synced: Boolean = false
)

@Entity(tableName = "fuel_transactions")
data class LocalFuel(
    @PrimaryKey val clientTransactionId: String,
    val sessionId: String,
    val odometer: Double,
    val quantity: Double,
    val rate: Double,
    val amount: Double,
    val fuelType: String,
    val synced: Boolean = false
)

@Entity(tableName = "expenses")
data class LocalExpense(
    @PrimaryKey val clientTransactionId: String,
    val sessionId: String,
    val amount: Double,
    val categoryId: String?,
    val synced: Boolean = false
)
