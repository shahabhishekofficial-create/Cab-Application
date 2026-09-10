package com.caboperations.driver.data

import android.content.Context

class SessionStateRepository(context: Context) {
    private val prefs = context.getSharedPreferences("session_state", Context.MODE_PRIVATE)

    data class State(val sessionId: String, val driverId: String, val vehicleId: String, val startOdometer: Double)

    fun open(sessionId: String, driverId: String, vehicleId: String, startOdometer: Double): State {
        val state = State(sessionId, driverId, vehicleId, startOdometer)
        prefs.edit()
            .putString("session_id", state.sessionId)
            .putString("driver_id", driverId)
            .putString("vehicle_id", vehicleId)
            .putString("start_odometer", startOdometer.toString())
            .putBoolean("open", true)
            .apply()
        return state
    }

    fun current(): State? {
        if (!prefs.getBoolean("open", false)) return null
        val id = prefs.getString("session_id", null) ?: return null
        val driver = prefs.getString("driver_id", null) ?: return null
        val vehicle = prefs.getString("vehicle_id", null) ?: return null
        val odo = prefs.getString("start_odometer", null)?.toDoubleOrNull() ?: return null
        return State(id, driver, vehicle, odo)
    }

    fun close() { prefs.edit().clear().apply() }
}
