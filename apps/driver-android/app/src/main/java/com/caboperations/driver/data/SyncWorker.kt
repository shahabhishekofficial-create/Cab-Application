package com.caboperations.driver.data

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters

/**
 * Durable offline sync worker. The queue is intentionally idempotent:
 * the server uses clientTransactionId as the authoritative de-duplication key.
 */
class SyncWorker(appContext: Context, params: WorkerParameters) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        val dao = CabDatabase.get(applicationContext).pendingTransactionDao()
        val pending = dao.pending()
        if (pending.isEmpty()) return Result.success()

        // API transport is wired in the next integration block. Keeping this worker
        // durable now ensures the local queue survives process death and restarts.
        return Result.retry()
    }
}
