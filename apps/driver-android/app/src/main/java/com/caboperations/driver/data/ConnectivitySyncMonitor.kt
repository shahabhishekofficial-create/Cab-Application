package com.caboperations.driver.data

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.util.Log
import com.caboperations.driver.BuildConfig

/** Starts an immediate sync whenever a validated internet-capable network becomes available. */
class ConnectivitySyncMonitor(private val context: Context) {
    private val appContext = context.applicationContext
    private val connectivity = appContext.getSystemService(ConnectivityManager::class.java)
    private var registered = false

    private val callback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            if (hasValidatedInternet(network)) {
                Log.i(TAG, "validated network available; requesting sync")
                SyncScheduler.resetAndEnqueue(appContext, BuildConfig.API_BASE_URL)
            }
        }

        override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) {
            if (capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)) {
                Log.i(TAG, "network capabilities validated; requesting sync")
                SyncScheduler.resetAndEnqueue(appContext, BuildConfig.API_BASE_URL)
            }
        }
    }

    fun register() {
        if (registered) return
        runCatching {
            connectivity.registerDefaultNetworkCallback(callback)
            registered = true
            Log.i(TAG, "connectivity listener registered")
        }.onFailure { Log.e(TAG, "connectivity listener registration failed", it) }
    }

    fun unregister() {
        if (!registered) return
        runCatching { connectivity.unregisterNetworkCallback(callback) }
        registered = false
    }

    private fun hasValidatedInternet(network: Network): Boolean =
        connectivity.getNetworkCapabilities(network)?.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) == true

    companion object { private const val TAG = "CabSync" }
}
