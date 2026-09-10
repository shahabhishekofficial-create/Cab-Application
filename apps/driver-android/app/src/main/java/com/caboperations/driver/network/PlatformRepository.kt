package com.caboperations.driver.network

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.net.HttpURLConnection
import java.net.URL

@Serializable
data class PlatformOption(val id: String, val name: String, val code: String? = null, val is_active: Boolean = true)

class PlatformRepository(private val baseUrl: String, private val accessToken: String) {
    private val json = Json { ignoreUnknownKeys = true }

    fun load(): Result<List<PlatformOption>> = runCatching {
        val connection = (URL(baseUrl.trimEnd('/') + "/v1/platforms").openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 10_000
            readTimeout = 15_000
            setRequestProperty("Authorization", "Bearer $accessToken")
        }
        try {
            val code = connection.responseCode
            val text = (if (code in 200..299) connection.inputStream else connection.errorStream)
                ?.bufferedReader()?.use { it.readText() }.orEmpty()
            if (code !in 200..299) error("PLATFORMS_LOAD_FAILED")
            val root = json.parseToJsonElement(text).jsonObject
            json.decodeFromJsonElement(ListSerializer(PlatformOption.serializer()), root["platforms"]!!)
        } finally { connection.disconnect() }
    }

    private object ListSerializer : kotlinx.serialization.KSerializer<List<PlatformOption>> by kotlinx.serialization.builtins.ListSerializer(PlatformOption.serializer())
}
