package com.caboperations.driver.network

import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets

class ApiClient(private val baseUrl: String) {
    data class Result(val success: Boolean, val retryable: Boolean, val error: String? = null)

    fun post(path: String, body: String, driverId: String?, vehicleId: String?): Result {
        val connection = (URL(baseUrl.trimEnd('/') + path).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 10_000
            readTimeout = 15_000
            doOutput = true
            setRequestProperty("Content-Type", "application/json")
            driverId?.let { setRequestProperty("x-driver-id", it) }
            vehicleId?.let { setRequestProperty("x-vehicle-id", it) }
        }
        return try {
            connection.outputStream.use { it.write(body.toByteArray(StandardCharsets.UTF_8)) }
            val code = connection.responseCode
            when {
                code in 200..299 -> Result(true, false)
                code == 408 || code == 429 || code >= 500 -> Result(false, true, "HTTP_$code")
                else -> Result(false, false, "HTTP_$code")
            }
        } catch (e: Exception) {
            Result(false, true, e.message ?: "NETWORK_ERROR")
        } finally {
            connection.disconnect()
        }
    }
}
