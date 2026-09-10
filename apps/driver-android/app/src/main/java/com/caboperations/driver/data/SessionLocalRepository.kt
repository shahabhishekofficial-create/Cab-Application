package com.caboperations.driver.data

import android.content.Context
import android.net.Uri
import androidx.room.withTransaction
import com.caboperations.driver.ocr.OdometerOcrResult
import com.caboperations.driver.ocr.OdometerVerifier
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.io.File
import java.time.Instant
import java.util.UUID

class SessionLocalRepository(private val context: Context) {
    private val db get() = CabDatabase.get(context)

    suspend fun queueStartSession(
        driverId: String,
        vehicleId: String,
        deviceId: String?,
        startOdometer: Double,
        startLat: Double?,
        startLng: Double?,
        startAccuracyM: Float?,
        startGpsAt: String?,
        startOdometerFilePath: String? = null,
        ocrResult: OdometerOcrResult? = null,
        ocrDecision: OdometerVerifier.Decision? = null
    ): String {
        val transactionId = UUID.randomUUID().toString()
        val sessionId = UUID.randomUUID().toString()
        val now = Instant.now().toString()
        val fileId = startOdometerFilePath?.let { UUID.randomUUID().toString() }
        val filePath = startOdometerFilePath?.let { Uri.parse(it).path }
        require(fileId == null || !filePath.isNullOrBlank()) { "INVALID_START_PHOTO" }
        if (filePath != null) require(File(filePath).exists()) { "START_PHOTO_MISSING" }
        val objectPath = fileId?.let { "sessions/$sessionId/start-odometer-$it.jpg" }

        val payload = buildJsonObject {
            put("clientTransactionId", transactionId)
            put("sessionId", sessionId)
            put("driverId", driverId)
            put("vehicleId", vehicleId)
            deviceId?.let { put("deviceId", it) }
            put("startedAt", now)
            put("startOdometer", startOdometer)
            startLat?.let { put("startLat", it) }
            startLng?.let { put("startLng", it) }
            startAccuracyM?.let { put("startAccuracyM", it) }
            startGpsAt?.let { put("startGpsAt", it) }
            fileId?.let { put("startOdometerFileId", it) }
            ocrResult?.reading?.let { put("ocrReading", it) }
            ocrResult?.let { put("ocrConfidence", it.confidence); put("ocrRawText", it.rawText) }
            ocrDecision?.let { put("ocrDecision", it.name) }
        }.toString()

        db.withTransaction {
            if (fileId != null && filePath != null) {
                db.pendingTransactionDao().insert(PendingTransaction(
                    UUID.randomUUID().toString(), "FILE_UPLOAD",
                    buildJsonObject {
                        put("fileId", fileId); put("localFilePath", filePath)
                        put("objectPath", objectPath!!); put("mimeType", "image/jpeg"); put("capturedAt", now)
                    }.toString(), System.currentTimeMillis()
                ))
            }
            db.localSessionDao().insert(LocalSession(sessionId, driverId, vehicleId, "OPEN", startOdometer))
            db.pendingTransactionDao().insert(PendingTransaction(transactionId, "SESSION_START", payload, System.currentTimeMillis()))
        }
        return sessionId
    }
}
