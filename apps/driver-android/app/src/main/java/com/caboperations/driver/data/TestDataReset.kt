package com.caboperations.driver.data

import android.content.Context
import android.util.Log
import com.caboperations.driver.BuildConfig

/** Deliberate debug-only local cleanup for the single QA vehicle. Never enabled for release builds. */
object TestDataReset {
    private const val TEST_DRIVER_ID = "117674b6-7ec1-4943-9989-3dc297e7bda5"
    private const val TEST_VEHICLE_ID = "d39c52a4-2e74-4ced-9f28-ea9d593f01b5"
    private const val TEST_REGISTRATION = "GJ01NT0088"

    suspend fun clearLocal(context: Context, driverId: String, vehicleId: String): Boolean {
        if (!BuildConfig.DEBUG || driverId != TEST_DRIVER_ID || vehicleId != TEST_VEHICLE_ID) return false
        val db = CabDatabase.get(context)
        val trips = db.localTripDao().deleteTestScope(TEST_DRIVER_ID, TEST_VEHICLE_ID)
        val fuel = db.localFuelDao().deleteTestScope(TEST_DRIVER_ID, TEST_VEHICLE_ID)
        val expenses = db.localExpenseDao().deleteTestScope(TEST_DRIVER_ID, TEST_VEHICLE_ID)
        val sessions = db.localSessionDao().deleteTestScope(TEST_DRIVER_ID, TEST_VEHICLE_ID)
        val pending = db.pendingTransactionDao().deleteTestScope(TEST_DRIVER_ID, TEST_VEHICLE_ID)
        Log.w("CabSync", "DELIBERATE QA RESET local scope=$TEST_REGISTRATION sessions=$sessions trips=$trips fuel=$fuel expenses=$expenses pending=$pending")
        return true
    }
}
