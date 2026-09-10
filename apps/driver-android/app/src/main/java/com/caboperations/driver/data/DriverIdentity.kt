package com.caboperations.driver.data

import android.content.Context
import android.provider.Settings

/** Authenticated driver/vehicle assignment cached locally for offline operation. */
class DriverIdentity(context: Context) {
    private val prefs = context.getSharedPreferences("driver_identity", Context.MODE_PRIVATE)
    val driverId: String? get() = prefs.getString("driver_id", null)
    val vehicleId: String? get() = prefs.getString("vehicle_id", null)
    val deviceId: String get() = prefs.getString("device_id", null)
        ?: Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID).also {
            prefs.edit().putString("device_id", it).apply()
        }

    fun configure(driverId: String, vehicleId: String) {
        prefs.edit().putString("driver_id", driverId).putString("vehicle_id", vehicleId).apply()
    }

    fun clearAssignment() {
        prefs.edit().remove("driver_id").remove("vehicle_id").apply()
    }
}
