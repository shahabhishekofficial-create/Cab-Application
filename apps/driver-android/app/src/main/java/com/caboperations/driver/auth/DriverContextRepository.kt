package com.caboperations.driver.auth

import com.caboperations.driver.BuildConfig
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.net.HttpURLConnection
import java.net.URL

@Serializable
data class DriverContext(
    val userId: String,
    val driverId: String,
    val displayName: String,
    val vehicleId: String? = null,
    val registrationNumber: String? = null
)

class DriverContextRepository {
    private val json = Json { ignoreUnknownKeys = true }

    fun load(accessToken: String): Result<DriverContext> = runCatching {
        val connection = (URL(BuildConfig.API_BASE_URL.trimEnd('/') + "/v1/me/driver-context").openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 10_000
            readTimeout = 15_000
            setRequestProperty("Authorization", "Bearer $accessToken")
        }
        try {
            val code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            val text = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
            if (code !in 200..299) {
                val message = runCatching { json.parseToJsonElement(text).jsonObject["error"]?.jsonPrimitive?.content }.getOrNull()
                error(message ?: "DRIVER_CONTEXT_FAILED")
            }
            val root = json.parseToJsonElement(text).jsonObject
            val context = root["context"] ?: error("DRIVER_CONTEXT_FAILED")
            json.decodeFromJsonElement(DriverContext.serializer(), context)
        } finally { connection.disconnect() }
    }
}
