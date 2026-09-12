package com.caboperations.driver.data

import android.content.Context
import android.os.SystemClock
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
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
    private const val FAILED_IMMEDIATE_COOLDOWN_MS = 10_000L
    private val immediateScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val immediateRunning = AtomicBoolean(false)
    private val nextImmediateAllowedAt = AtomicLong(0L)

    /**
     * One immediate attempt for foreground responsiveness plus a delayed,
     * durable WorkManager fallback. Repeated lifecycle callbacks cannot create
     * a burst of identical API attempts.
     */
    fun enqueue(context: Context, apiBaseUrl: String = BuildConfig.API_BASE_URL) {
        val appContext = context.applicationContext

        // Schedule the durable fallback first so process death cannot lose it.
        // Delay it so a successful immediate sync is not followed by a duplicate
        // request a few milliseconds later.
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()
        val request = OneTimeWorkRequestBuilder<SyncWorker>()
            .setConstraints(constraints)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 10, TimeUnit.SECONDS)
            .setInitialDelay(15, TimeUnit.SECONDS)
            .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
            .setInputData(workDataOf(SyncWorker.KEY_BASE_URL to apiBaseUrl))
            .build()
        WorkManager.getInstance(appContext).enqueueUniqueWork(
            UNIQUE_WORK,
            ExistingWorkPolicy.KEEP,
            request
        )

        val now = SystemClock.elapsedRealtime()
        if (now < nextImmediateAllowedAt.get()) return
        if (!immediateRunning.compareAndSet(false, true)) return

        immediateScope.launch {
            try {
                val success = SyncEngine.run(appContext, apiBaseUrl)
                if (!success) {
                    // WorkManager owns subsequent retries; don't let repeated
                    // onResume/local-save callbacks hammer the same transaction.
                    nextImmediateAllowedAt.set(SystemClock.elapsedRealtime() + FAILED_IMMEDIATE_COOLDOWN_MS)
                }
            } finally {
                immediateRunning.set(false)
            }
        }
    }
}
