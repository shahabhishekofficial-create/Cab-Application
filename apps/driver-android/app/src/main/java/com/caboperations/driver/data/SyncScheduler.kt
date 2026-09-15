package com.caboperations.driver.data

import android.content.Context
import android.os.SystemClock
import androidx.work.BackoffPolicy
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.caboperations.driver.BuildConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

object SyncScheduler {
    private const val UNIQUE_WORK = "cab-offline-sync"
    private const val PERIODIC_WORK = "cab-offline-sync-periodic"
    private const val FAILED_IMMEDIATE_COOLDOWN_MS = 10_000L
    private val immediateScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val immediateRunning = AtomicBoolean(false)
    private val nextImmediateAllowedAt = AtomicLong(0L)

    fun ensurePeriodic(context: Context, apiBaseUrl: String = BuildConfig.API_BASE_URL) {
        val appContext = context.applicationContext
        runCatching {
            val request = PeriodicWorkRequestBuilder<SyncWorker>(15, TimeUnit.MINUTES)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 10, TimeUnit.SECONDS)
                .setInputData(workDataOf(SyncWorker.KEY_BASE_URL to apiBaseUrl))
                .addTag("cab-sync-periodic")
                .build()
            WorkManager.getInstance(appContext).enqueueUniquePeriodicWork(PERIODIC_WORK, ExistingPeriodicWorkPolicy.KEEP, request)
        }
    }

    fun enqueue(context: Context, apiBaseUrl: String = BuildConfig.API_BASE_URL) {
        val appContext = context.applicationContext
        runCatching {
            val request = OneTimeWorkRequestBuilder<SyncWorker>()
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 10, TimeUnit.SECONDS)
                .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
                .setInputData(workDataOf(SyncWorker.KEY_BASE_URL to apiBaseUrl))
                .addTag("cab-sync")
                .build()
            WorkManager.getInstance(appContext).enqueueUniqueWork(UNIQUE_WORK, ExistingWorkPolicy.KEEP, request)
        }

        val now = SystemClock.elapsedRealtime()
        if (now < nextImmediateAllowedAt.get() || !immediateRunning.compareAndSet(false, true)) return
        immediateScope.launch {
            try {
                val success = runCatching { SyncEngine.run(appContext, apiBaseUrl) }.getOrDefault(false)
                if (!success) nextImmediateAllowedAt.set(SystemClock.elapsedRealtime() + FAILED_IMMEDIATE_COOLDOWN_MS)
            } finally { immediateRunning.set(false) }
        }
    }

    fun resetAndEnqueue(context: Context, apiBaseUrl: String = BuildConfig.API_BASE_URL) {
        val appContext = context.applicationContext
        immediateScope.launch {
            runCatching { WorkManager.getInstance(appContext).cancelUniqueWork(UNIQUE_WORK) }
            enqueue(appContext, apiBaseUrl)
        }
    }
}
