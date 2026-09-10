package com.caboperations.driver.data

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

object PendingTransactionJson {
    fun withSessionIdentity(payload: JsonObject, driverId: String, vehicleId: String): String =
        buildJsonObject {
            payload.forEach { (key, value) -> put(key, value) }
            put("driverId", driverId)
            put("vehicleId", vehicleId)
        }.toString()

    fun parse(json: String): JsonObject = Json.parseToJsonElement(json) as JsonObject
}
