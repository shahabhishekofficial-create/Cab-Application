package com.caboperations.driver.data

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkManager
import androidx.work.workDataOf
import java.util.concurrent.TimeUnit

object SyncScheduler {
    private const val UNIQUE_WORK = "cab-offline-sync"

    /**
     * Every locally committed entry triggers an immediate sync attempt when the
     * network is available. REPLACE is intentional: a previously stuck/retrying
     * worker must never block a new explicit Sync Now request.
     */
    fun enqueue(context: Context, apiBaseUrl: String) {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()
        val request = OneTimeWorkRequestBuilder<SyncWorker>()
            .setConstraints(constraints)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 10, TimeUnit.SECONDS)
            .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
            .setInputData(workDataOf(SyncWorker.KEY_BASE_URL to apiBaseUrl))
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            UNIQUE_WORK,
            ExistingWorkPolicy.REPLACE,
            request
        )
    }
}
