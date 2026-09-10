package com.caboperations.driver.location

/** Minimum GPS evidence captured at session boundaries and optionally on transactions. */
data class LocationSnapshot(
    val latitude: Double,
    val longitude: Double,
    val accuracyMeters: Float,
    val capturedAtEpochMs: Long
) {
    fun isUsable(maxAccuracyMeters: Float = 100f): Boolean =
        accuracyMeters >= 0f && accuracyMeters <= maxAccuracyMeters &&
            latitude in -90.0..90.0 && longitude in -180.0..180.0
}
