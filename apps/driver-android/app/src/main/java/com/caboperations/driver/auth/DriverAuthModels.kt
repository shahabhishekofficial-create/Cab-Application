package com.caboperations.driver.auth

import kotlinx.serialization.Serializable

@Serializable
data class DriverContext(
    val userId: String,
    val driverId: String,
    val displayName: String,
    val vehicleId: String? = null,
    val registrationNumber: String? = null
)
