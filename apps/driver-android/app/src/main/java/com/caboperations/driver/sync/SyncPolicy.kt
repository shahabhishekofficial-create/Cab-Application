package com.caboperations.driver.sync

/** Central retry policy for durable mobile synchronization. */
object SyncPolicy {
    const val MAX_BATCH_SIZE = 25
    const val MAX_RETRY_ATTEMPTS = 10

    fun shouldRetry(httpCode: Int?): Boolean =
        httpCode == null || httpCode == 408 || httpCode == 429 || httpCode in 500..599

    fun shouldRetryAuthentication(refreshSucceeded: Boolean): Boolean = !refreshSucceeded
}
