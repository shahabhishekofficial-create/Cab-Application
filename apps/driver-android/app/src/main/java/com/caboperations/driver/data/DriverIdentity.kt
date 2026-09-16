package com.caboperations.driver.data

import android.content.Context
import android.provider.Settings
import com.caboperations.driver.auth.DriverContext
import kotlinx.serialization.json.Json

/** Authenticated driver/vehicle assignment and last known profile cached locally for offline operation. */
class DriverIdentity(context: Context) {
    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences("driver_identity", Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true }

    val driverId: String? get() = prefs.getString("driver_id", null)
    val vehicleId: String? get() = prefs.getString("vehicle_id", null)
    val deviceId: String get() = prefs.getString("device_id", null)
        ?: Settings.Secure.getString(appContext.contentResolver, Settings.Secure.ANDROID_ID).also {
            prefs.edit().putString("device_id", it).apply()
        }

    fun configure(driverId: String, vehicleId: String) {
        prefs.edit().putString("driver_id", driverId).putString("vehicle_id", vehicleId).apply()
    }

    fun cacheContext(context: DriverContext) {
        configure(context.driverId, context.vehicleId ?: return)
        prefs.edit().putString("driver_context", json.encodeToString(context)).apply()
    }

    fun cachedContext(): DriverContext? = runCatching {
        prefs.getString("driver_context", null)?.let { json.decodeFromString<DriverContext>(it) }
    }.getOrNull()

    fun clearAssignment() {
        prefs.edit().remove("driver_id").remove("vehicle_id").remove("driver_context").apply()
    }
}
