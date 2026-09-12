package com.caboperations.driver.network

import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

class ApiClient(private val baseUrl: String, private val accessToken: String? = null) {
    data class Result(val success: Boolean, val retryable: Boolean, val error: String? = null, val authExpired: Boolean = false)

    private fun resultForCode(connection: HttpURLConnection): Result {
        val code = connection.responseCode
        val classified = classifyHttpCode(code)
        if (classified.success || classified.authExpired) return classified
        val stream = runCatching { connection.errorStream ?: connection.inputStream }.getOrNull() ?: return classified
        val body = runCatching { stream.use { it.readBytes().toString(StandardCharsets.UTF_8) } }.getOrNull().orEmpty()
        if (body.isBlank()) return classified
        val serverError = runCatching {
            Json.parseToJsonElement(body).jsonObject["error"]?.jsonPrimitive?.content
        }.getOrNull()
        val detail = serverError?.takeIf { it.isNotBlank() }
        return if (detail != null) classified.copy(error = detail) else classified
    }

    fun post(path: String, body: String, driverId: String?, vehicleId: String?): Result {
        val connection = (URL(baseUrl.trimEnd('/') + path).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"; connectTimeout = 10_000; readTimeout = 15_000; doOutput = true
            setRequestProperty("Content-Type", "application/json")
            accessToken?.let { setRequestProperty("Authorization", "Bearer $it") }
            driverId?.let { setRequestProperty("x-driver-id", it) }
            vehicleId?.let { setRequestProperty("x-vehicle-id", it) }
        }
        return try {
            connection.outputStream.use { it.write(body.toByteArray(StandardCharsets.UTF_8)) }
            resultForCode(connection)
        } catch (e: Exception) { Result(false, true, e.message ?: "NETWORK_ERROR") }
        finally { connection.disconnect() }
    }

    fun uploadFile(path: String, fileId: String, objectPath: String, mimeType: String, bytes: ByteArray, capturedAt: String?): Result {
        val connection = (URL(baseUrl.trimEnd('/') + path).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"; connectTimeout = 10_000; readTimeout = 30_000; doOutput = true
            setRequestProperty("Content-Type", mimeType); setRequestProperty("x-file-id", fileId); setRequestProperty("x-object-path", objectPath)
            accessToken?.let { setRequestProperty("Authorization", "Bearer $it") }
            capturedAt?.let { setRequestProperty("x-captured-at", it) }
        }
        return try {
            connection.outputStream.use { it.write(bytes) }
            resultForCode(connection)
        } catch (e: Exception) { Result(false, true, e.message ?: "NETWORK_ERROR") }
        finally { connection.disconnect() }
    }

    companion object {
        internal fun classifyHttpCode(code: Int): Result = when {
            code in 200..299 -> Result(true, false)
            code == 401 -> Result(false, false, "AUTH_EXPIRED", authExpired = true)
            code == 408 || code == 429 || code >= 500 -> Result(false, true, "HTTP_$code")
            code in 400..499 -> Result(false, false, "HTTP_$code")
            else -> Result(false, true, "HTTP_$code")
        }
    }
}
