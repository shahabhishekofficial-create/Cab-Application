package com.caboperations.driver.data

import android.content.Context
import android.net.Uri
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.UUID

/** Persists camera/content URIs into app-owned storage before an offline upload is queued. */
object LocalPhotoStore {
    fun persist(context: Context, uriString: String): String {
        require(uriString.isNotBlank()) { "PHOTO_URI_EMPTY" }
        val uri = Uri.parse(uriString)
        val destinationDir = File(context.filesDir, "pending-photos").apply { mkdirs() }
        val destination = File(destinationDir, "${UUID.randomUUID()}.jpg")

        if (uri.scheme.equals("file", ignoreCase = true)) {
            val source = File(requireNotNull(uri.path) { "PHOTO_PATH_MISSING" })
            require(source.exists() && source.isFile) { "PHOTO_MISSING" }
            FileInputStream(source).use { input ->
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
