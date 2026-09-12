package com.caboperations.driver.sync

/** Central retry policy for durable mobile synchronization. */
object SyncPolicy {
    const val MAX_BATCH_SIZE = 25
    // A single transaction is never hammered indefinitely. After five failed
    // attempts it is quarantined until a later explicit recovery/reset path.
    const val MAX_RETRY_ATTEMPTS = 5

    fun shouldRetry(httpCode: Int?): Boolean =
        httpCode == null || httpCode == 408 || httpCode == 429 || httpCode in 500..599

    fun shouldRetryAuthentication(refreshSucceeded: Boolean): Boolean = !refreshSucceeded
}
