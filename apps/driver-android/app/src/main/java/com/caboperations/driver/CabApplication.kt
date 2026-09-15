package com.caboperations.driver

import android.app.Application
import com.caboperations.driver.data.ConnectivitySyncMonitor
import com.caboperations.driver.data.SyncScheduler

class CabApplication : Application() {
    private lateinit var connectivitySyncMonitor: ConnectivitySyncMonitor

    override fun onCreate() {
        super.onCreate()
        connectivitySyncMonitor = ConnectivitySyncMonitor(this).also { it.register() }
        SyncScheduler.resetAndEnqueue(this, BuildConfig.API_BASE_URL)
    }
}
