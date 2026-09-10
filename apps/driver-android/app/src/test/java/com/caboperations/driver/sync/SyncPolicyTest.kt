package com.caboperations.driver.sync

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SyncPolicyTest {
    @Test
    fun networkAndTransientServerFailuresRetry() {
        assertTrue(SyncPolicy.shouldRetry(null))
        assertTrue(SyncPolicy.shouldRetry(408))
        assertTrue(SyncPolicy.shouldRetry(429))
        assertTrue(SyncPolicy.shouldRetry(500))
        assertTrue(SyncPolicy.shouldRetry(503))
    }

    @Test
    fun clientValidationFailuresDoNotRetry() {
        assertFalse(SyncPolicy.shouldRetry(400))
        assertFalse(SyncPolicy.shouldRetry(401))
        assertFalse(SyncPolicy.shouldRetry(403))
        assertFalse(SyncPolicy.shouldRetry(404))
    }

    @Test
    fun failedAuthenticationRefreshRequestsRetry() {
        assertTrue(SyncPolicy.shouldRetryAuthentication(false))
        assertFalse(SyncPolicy.shouldRetryAuthentication(true))
    }
}
