package com.caboperations.driver.data

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.caboperations.driver.BuildConfig

class SyncWorker(appContext: Context, params: WorkerParameters) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        val baseUrl = inputData.getString(KEY_BASE_URL) ?: BuildConfig.API_BASE_URL
        Log.i(TAG, "WorkManager run start id=$id attempt=$runAttemptCount")
        val success = SyncEngine.run(applicationContext, baseUrl)
        Log.i(TAG, "WorkManager run end id=$id attempt=$runAttemptCount success=$success")
        return if (success) Result.success() else Result.retry()
    }

    companion object {
        const val KEY_BASE_URL = "api_base_url"
        private const val TAG = "CabSync"
    }
}
