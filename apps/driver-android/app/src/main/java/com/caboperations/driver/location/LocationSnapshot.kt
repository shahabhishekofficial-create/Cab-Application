package com.caboperations.driver.location

/** GPS evidence captured for operational events. */
data class LocationSnapshot(
    val latitude: Double,
    val longitude: Double,
    val accuracyMeters: Float,
    val capturedAtEpochMs: Long
) {
    fun isUsable(
        maxAccuracyMeters: Float = MAX_ACCURACY_METERS,
        maxAgeMs: Long = MAX_AGE_MS,
        nowEpochMs: Long = System.currentTimeMillis()
    ): Boolean {
        val age = nowEpochMs - capturedAtEpochMs
        return accuracyMeters >= 0f && accuracyMeters <= maxAccuracyMeters &&
            age in 0..maxAgeMs &&
            latitude in -90.0..90.0 && longitude in -180.0..180.0
    }

    companion object {
        const val MAX_ACCURACY_METERS = 50f
        const val MAX_AGE_MS = 60_000L
    }
}
