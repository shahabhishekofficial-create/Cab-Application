package com.caboperations.driver.capture

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner

class CameraPreviewController(private val context: Context) {
    fun bind(owner: LifecycleOwner, previewView: PreviewView, onReady: (ImageCapture) -> Unit) {
        bindInternal(owner, previewView) { capture, _ -> onReady(capture) }
    }

    fun bindWithCamera(owner: LifecycleOwner, previewView: PreviewView, onReady: (ImageCapture, Camera) -> Unit) {
        bindInternal(owner, previewView, onReady)
    }

    private fun bindInternal(owner: LifecycleOwner, previewView: PreviewView, onReady: (ImageCapture, Camera) -> Unit) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) return
        val future = ProcessCameraProvider.getInstance(context)
        future.addListener({
            runCatching {
                val provider = future.get()
                val preview = Preview.Builder().build().also { it.surfaceProvider = previewView.surfaceProvider }
                val capture = ImageCapture.Builder().setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY).build()
                provider.unbindAll()
                val camera = provider.bindToLifecycle(owner, CameraSelector.DEFAULT_BACK_CAMERA, preview, capture)
                onReady(capture, camera)
            }
        }, ContextCompat.getMainExecutor(context))
    }
}
