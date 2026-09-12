package com.caboperations.driver.data

import android.content.Context
import androidx.room.withTransaction
import com.caboperations.driver.ocr.OdometerOcrResult
import com.caboperations.driver.ocr.OdometerVerifier
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.time.Instant
import java.util.UUID

class SessionCloseLocalRepository(private val context: Context) {
    private val db get() = CabDatabase.get(context)

    suspend fun queueCloseSession(
        sessionId: String, driverId: String, vehicleId: String, closeOdometer: Double,
        closeLat: Double?, closeLng: Double?, closeAccuracyM: Float?, closeGpsAt: String?,
        closeOdometerFilePath: String? = null, reportedTripCount: Int, reportedIncome: Double,
        notes: String? = null, ocrResult: OdometerOcrResult? = null, ocrDecision: OdometerVerifier.Decision? = null
    ) {
        require(closeOdometer >= 0) { "Closing odometer must be non-negative" }
        require(reportedTripCount >= 0) { "Reported trip count must be non-negative" }
        require(reportedIncome >= 0) { "Reported income must be non-negative" }

        val session = db.localSessionDao().find(sessionId) ?: throw IllegalStateException("SESSION_NOT_FOUND")
        require(session.driverId == driverId) { "SESSION_DRIVER_MISMATCH" }
        require(session.vehicleId == vehicleId) { "SESSION_VEHICLE_MISMATCH" }
        require(session.status == "OPEN") { "SESSION_NOT_OPEN" }
        require(db.localTripDao().active(sessionId) == null) { "ACTIVE_TRIP_MUST_BE_ENDED" }
        require(closeOdometer >= session.startOdometer) { "INVALID_CLOSE_ODOMETER" }
        OdometerGuard.requireAtLeast(db, sessionId, closeOdometer, "Closing odometer")

        val transactionId = UUID.randomUUID().toString()
        val closedAt = Instant.now().toString()
        val fileId = closeOdometerFilePath?.let { UUID.randomUUID().toString() }
        val filePath = closeOdometerFilePath?.let { LocalPhotoStore.persist(context, it) }
        val objectPath = fileId?.let { "sessions/$sessionId/close-odometer-$it.jpg" }

        val payload = buildJsonObject {
            put("clientTransactionId", transactionId); put("sessionId", sessionId); put("driverId", driverId); put("vehicleId", vehicleId)
            put("closedAt", closedAt); put("closeOdometer", closeOdometer); closeLat?.let { put("closeLat", it) }; closeLng?.let { put("closeLng", it) }
            closeAccuracyM?.let { put("closeAccuracyM", it) }; closeGpsAt?.let { put("closeGpsAt", it) }; fileId?.let { put("closeOdometerFileId", it) }
            put("reportedTripCount", reportedTripCount); put("reportedIncome", reportedIncome)
            ocrResult?.reading?.let { put("ocrReading", it) }; ocrResult?.let { put("ocrConfidence", it.confidence); put("ocrRawText", it.rawText) }
            ocrDecision?.let { put("ocrDecision", it.name) }; notes?.let { put("notes", it) }
        }.toString()

        db.withTransaction {
            if (fileId != null && filePath != null) {
                db.pendingTransactionDao().insert(PendingTransaction(UUID.randomUUID().toString(), "FILE_UPLOAD", buildJsonObject {
                    put("fileId", fileId); put("localFilePath", filePath); put("objectPath", objectPath!!); put("mimeType", "image/jpeg"); put("capturedAt", closedAt)
                }.toString(), System.currentTimeMillis()))
            }
            db.localSessionDao().markClosed(sessionId, closeOdometer)
            db.pendingTransactionDao().insert(PendingTransaction(transactionId, "SESSION_CLOSE", payload, System.currentTimeMillis()))
        }
    }
}
