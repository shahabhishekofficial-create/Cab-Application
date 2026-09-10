package com.caboperations.driver.capture

import android.content.Context
import android.net.Uri

/**
 * Represents an odometer capture before it is committed to a session.
 * CameraX owns the actual camera lifecycle; this model keeps capture metadata
 * together with the eventual persisted file URI.
 */
data class OdometerCapture(
    val photoUri: Uri,
    val capturedAtEpochMs: Long,
    val odometerReading: Double? = null,
    val ocrConfidence: Float? = null
)

fun OdometerCapture.isValidForUpload(): Boolean =
    photoUri.toString().isNotBlank() && capturedAtEpochMs > 0L

class CaptureDirectory(context: Context) {
    val directory = context.filesDir.resolve("odometer")
    init { directory.mkdirs() }
}
