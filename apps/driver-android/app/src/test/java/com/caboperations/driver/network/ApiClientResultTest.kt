package com.caboperations.driver.network

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ApiClientResultTest {
    @Test
    fun httpClassificationMatchesSyncPolicy() {
        assertEquals(ApiClient.Result(true, false), ApiClient.classifyHttpCode(200))
        assertEquals(ApiClient.Result(false, false, "AUTH_EXPIRED", authExpired = true), ApiClient.classifyHttpCode(401))
        assertEquals(ApiClient.Result(false, true, "HTTP_408"), ApiClient.classifyHttpCode(408))
        assertEquals(ApiClient.Result(false, false, "HTTP_409"), ApiClient.classifyHttpCode(409))
        assertEquals(ApiClient.Result(false, true, "HTTP_429"), ApiClient.classifyHttpCode(429))
        assertEquals(ApiClient.Result(false, true, "HTTP_500"), ApiClient.classifyHttpCode(500))
        assertEquals(ApiClient.Result(false, false, "HTTP_422"), ApiClient.classifyHttpCode(422))
    }

    @Test
    fun structuredServerErrorsAreExtractedForAllTransactionEndpoints() {
        assertEquals("SESSION_ALREADY_OPEN", ApiClient.extractServerError("{\"error\":\"SESSION_ALREADY_OPEN\",\"message\":\"already open\"}"))
        assertEquals("ODOMETER_REGRESSION", ApiClient.extractServerError("{\"error\":{\"code\":\"ODOMETER_REGRESSION\",\"message\":\"regression\"}}"))
        assertEquals("FUEL_AMOUNT_MISMATCH", ApiClient.extractServerError("{\"code\":\"FUEL_AMOUNT_MISMATCH\"}"))
        assertEquals("TRANSACTION_FAILED", ApiClient.extractServerError("{\"error\":\"TRANSACTION_FAILED\"}"))
        assertNull(ApiClient.extractServerError("not-json"))
    }
}
