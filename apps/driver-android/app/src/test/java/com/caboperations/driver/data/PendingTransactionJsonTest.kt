package com.caboperations.driver.data

import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PendingTransactionJsonTest {
    @Test
    fun `session identity overwrites stale payload identity`() {
        val payload = buildJsonObject {
            put("clientTransactionId", "11111111-1111-4111-8111-111111111111")
            put("driverId", "old-driver")
            put("vehicleId", "old-vehicle")
            put("amount", 250)
        }

        val result = PendingTransactionJson.parse(
            PendingTransactionJson.withSessionIdentity(payload, "new-driver", "new-vehicle")
        )

        assertEquals("new-driver", result["driverId"]?.toString()?.trim('"'))
        assertEquals("new-vehicle", result["vehicleId"]?.toString()?.trim('"'))
        assertTrue(result["clientTransactionId"] != null)
        assertEquals("250", result["amount"]?.toString())
    }
}
