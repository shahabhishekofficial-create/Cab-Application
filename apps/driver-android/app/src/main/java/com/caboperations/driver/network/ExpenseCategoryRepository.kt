package com.caboperations.driver.network

import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.jsonObject

@Serializable
data class ExpenseCategoryOption(
    val id: String,
    val name: String,
    val code: String? = null,
    val is_active: Boolean = true,
)

class ExpenseCategoryRepository(
    private val baseUrl: String,
    private val accessToken: String,
) {
    private val json = Json { ignoreUnknownKeys = true }

    fun load(): Result<List<ExpenseCategoryOption>> = runCatching {
        val connection = java.net.URL("${baseUrl.trimEnd('/')}/v1/expense-categories").openConnection() as java.net.HttpURLConnection
        connection.requestMethod = "GET"
        connection.connectTimeout = 10_000
        connection.readTimeout = 10_000
        connection.setRequestProperty("Authorization", "Bearer $accessToken")
        try {
            if (connection.responseCode !in 200..299) error("HTTP_${connection.responseCode}")
            val body = connection.inputStream.bufferedReader().use { it.readText() }
            val root = json.parseToJsonElement(body).jsonObject
            json.decodeFromJsonElement(ListSerializer(ExpenseCategoryOption.serializer()), root.getValue("categories"))
        } finally {
            connection.disconnect()
        }
    }
}
