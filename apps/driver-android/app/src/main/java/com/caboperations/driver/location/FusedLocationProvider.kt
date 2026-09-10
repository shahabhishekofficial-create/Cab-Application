package com.caboperations.driver.location

import android.annotation.SuppressLint
import android.content.Context
import android.location.LocationManager
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource

class FusedLocationProvider(context: Context) {
    private val client = LocationServices.getFusedLocationProviderClient(context)
    private val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager

    fun isLocationEnabled(): Boolean =
        locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) ||
            locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)

    @SuppressLint("MissingPermission")
    fun currentLocation(onResult: (LocationSnapshot?, String?) -> Unit) {
        if (!isLocationEnabled()) {
            onResult(null, "LOCATION_DISABLED")
            return
        }
        client.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, CancellationTokenSource().token)
            .addOnSuccessListener { location ->
                if (location == null) {
                    onResult(null, "GPS_NOT_FOUND")
                } else {
                    onResult(
                        LocationSnapshot(location.latitude, location.longitude, location.accuracy, location.time),
                        null
                    )
                }
            }
            .addOnFailureListener { error -> onResult(null, error.message ?: "GPS_FAILED") }
    }
}
