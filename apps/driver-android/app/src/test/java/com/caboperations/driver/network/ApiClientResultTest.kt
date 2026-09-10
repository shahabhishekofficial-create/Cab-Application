package com.caboperations.driver.network

import org.junit.Assert.assertEquals
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
}
