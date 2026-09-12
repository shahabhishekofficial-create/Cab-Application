package com.caboperations.driver.data

/**
 * Local monotonic-odometer barrier. The phone rejects regressions before anything
 * enters the sync queue; the database applies the same rule again on the server.
 */
object OdometerGuard {
    suspend fun requireAtLeast(db: CabDatabase, sessionId: String, odometer: Double, label: String = "Odometer") {
        require(odometer.isFinite() && odometer >= 0) { "$label must be a valid non-negative number" }
        val session = db.localSessionDao().find(sessionId) ?: throw IllegalStateException("SESSION_NOT_FOUND")
        val tripStart = db.localTripDao().maxStartOdometer(sessionId)
        val tripEnd = db.localTripDao().maxEndOdometer(sessionId)
        val fuel = db.localFuelDao().maxOdometer(sessionId)
        val previous = listOfNotNull(session.startOdometer, tripStart, tripEnd, fuel).maxOrNull() ?: session.startOdometer
        require(odometer >= previous) {
            "$label cannot be below the latest recorded odometer of ${format(previous)} km"
        }
    }

    private fun format(value: Double): String = if (value % 1.0 == 0.0) value.toLong().toString() else "%.2f".format(value)
}
