package com.caboperations.driver.capture

import android.content.Context
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.core.content.ContextCompat
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Captures directly into app-owned storage and returns only the absolute file path. */
class CameraCapture(private val context: Context) {
    fun capture(imageCapture: ImageCapture, prefix: String, onResult: (Result<String>) -> Unit) {
        val dir = File(context.filesDir, "captures").apply { mkdirs() }
        val name = "${prefix}_${SimpleDateFormat("yyyyMMdd_HHmmss_SSS", Locale.US).format(Date())}.jpg"
        val file = File(dir, name)
        val options = ImageCapture.OutputFileOptions.Builder(file).build()

        imageCapture.takePicture(
            options,
            ContextCompat.getMainExecutor(context),
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                    if (file.exists() && file.isFile && file.length() > 0L) {
                        onResult(Result.success(file.absolutePath))
                    } else {
                        onResult(Result.failure(IllegalStateException("CAPTURE_FILE_EMPTY")))
                    }
                }

                override fun onError(exception: ImageCaptureException) {
                    onResult(Result.failure(exception))
                }
            }
        )
    }
}
