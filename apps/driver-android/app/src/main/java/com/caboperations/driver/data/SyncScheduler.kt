package com.caboperations.driver.data

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
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

object SyncScheduler {
    private const val UNIQUE_WORK = "cab-offline-sync"
    private val immediateScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /**
     * Start a real network sync immediately, while also scheduling WorkManager
     * as the durable retry path for offline/background/process-death cases.
     * The driver never needs to press a sync button.
     */
    fun enqueue(context: Context, apiBaseUrl: String = BuildConfig.API_BASE_URL) {
        val appContext = context.applicationContext
        immediateScope.launch {
            SyncEngine.run(appContext, apiBaseUrl)
        }

        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()
        val request = OneTimeWorkRequestBuilder<SyncWorker>()
            .setConstraints(constraints)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 10, TimeUnit.SECONDS)
            .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
            .setInputData(workDataOf(SyncWorker.KEY_BASE_URL to apiBaseUrl))
            .build()
        WorkManager.getInstance(appContext).enqueueUniqueWork(
            UNIQUE_WORK,
            ExistingWorkPolicy.REPLACE,
            request
        )
    }
}
