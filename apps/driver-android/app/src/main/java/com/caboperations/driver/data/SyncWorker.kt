package com.caboperations.driver.data

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.caboperations.driver.BuildConfig

class SyncWorker(appContext: Context, params: WorkerParameters) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        val baseUrl = inputData.getString(KEY_BASE_URL) ?: BuildConfig.API_BASE_URL
        return if (SyncEngine.run(applicationContext, baseUrl)) Result.success() else Result.retry()
    }

    companion object {
        const val KEY_BASE_URL = "api_base_url"
    }
}
