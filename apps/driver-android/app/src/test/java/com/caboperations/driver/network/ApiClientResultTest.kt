package com.caboperations.driver.network

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ApiClientResultTest {
    @Test
    fun retryClassificationKeepsConflictsPermanent() {
        val policy = mapOf(
            401 to Pair(false, true),
            408 to Pair(true, false),
            409 to Pair(false, false),
            429 to Pair(true, false),
            500 to Pair(true, false),
            400 to Pair(false, false),
        )
        // Classification is intentionally mirrored here as a regression contract;
        // HTTP 409 must not cause an endless offline retry loop.
        policy.forEach { (code, expected) ->
            val retryable = code == 408 || code == 429 || code >= 500
            val authExpired = code == 401
            assertTrue("retry mismatch for $code", retryable == expected.first)
            assertTrue("auth mismatch for $code", authExpired == expected.second)
        }
    }
}
