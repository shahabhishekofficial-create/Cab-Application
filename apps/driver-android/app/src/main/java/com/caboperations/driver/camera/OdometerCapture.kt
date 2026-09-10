package com.caboperations.driver.camera

/** Metadata recorded alongside every odometer/proof photo. */
data class OdometerCapture(
    val filePath: String,
    val capturedAtEpochMs: Long,
    val odometerReading: Double?,
    val sha256: String? = null
)

fun validateOdometerReading(reading: Double): Boolean = reading.isFinite() && reading >= 0.0
