package com.caboperations.driver.auth

import com.caboperations.driver.BuildConfig
import com.caboperations.driver.CabApplication
import com.caboperations.driver.data.CabDatabase
import com.caboperations.driver.data.DriverIdentity
import com.caboperations.driver.data.VehicleEntity
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.net.HttpURLConnection
import java.net.URL

class DriverContextRepository {
    private val json = Json { ignoreUnknownKeys = true }

    fun cached(): DriverContext? = DriverIdentity(CabApplication.instance).cachedContext()

    fun load(accessToken: String): DriverContext = runCatching {
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
            val driverContext = json.decodeFromJsonElement(DriverContext.serializer(), context)
            val vehicleId = driverContext.vehicleId
            if (!vehicleId.isNullOrBlank()) {
                CabDatabase.get(CabApplication.instance).vehicleDao().upsert(
                    VehicleEntity(
                        vehicleId = vehicleId,
                        registrationNumber = driverContext.registrationNumber,
                        currentOdometer = driverContext.currentOdometer
                    )
                )
            }
            DriverIdentity(CabApplication.instance).cacheContext(driverContext)
            driverContext
        } finally {
            connection.disconnect()
        }
    }.getOrThrow()
}
