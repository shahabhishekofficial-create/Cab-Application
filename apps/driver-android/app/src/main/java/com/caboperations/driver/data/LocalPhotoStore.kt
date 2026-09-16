package com.caboperations.driver.data

import android.content.Context
import android.net.Uri
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.UUID

/** Persists camera/content URIs or absolute camera paths into app-owned storage before an offline upload is queued. */
object LocalPhotoStore {
    fun persist(context: Context, uriString: String): String {
        require(uriString.isNotBlank()) { "PHOTO_URI_EMPTY" }
        val uri = Uri.parse(uriString)
        val destinationDir = File(context.filesDir, "pending-photos").apply { mkdirs() }
        val destination = File(destinationDir, "${UUID.randomUUID()}.jpg")

        val sourceFile = when {
            uri.scheme.equals("file", ignoreCase = true) -> File(requireNotNull(uri.path) { "PHOTO_PATH_MISSING" })
            uri.scheme.isNullOrBlank() -> File(uriString)
            else -> null
        }

        if (sourceFile != null) {
            require(sourceFile.exists() && sourceFile.isFile) { "PHOTO_MISSING" }
            FileInputStream(sourceFile).use { input ->
                FileOutputStream(destination).use { output -> input.copyTo(output) }
            }
        } else {
            val input = context.contentResolver.openInputStream(uri) ?: throw IllegalStateException("PHOTO_UNREADABLE")
            input.use { source ->
                FileOutputStream(destination).use { output -> source.copyTo(output) }
            }
        }

        require(destination.length() > 0) { "PHOTO_EMPTY" }
        return destination.absolutePath
    }
}
