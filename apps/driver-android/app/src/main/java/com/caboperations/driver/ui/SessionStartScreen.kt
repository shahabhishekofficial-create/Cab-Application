package com.caboperations.driver.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.ImageCapture
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.caboperations.driver.capture.CameraCapture
import com.caboperations.driver.capture.CameraPreviewController
import com.caboperations.driver.location.FusedLocationProvider
import com.caboperations.driver.location.LocationSnapshot

@Composable
fun SessionStartScreen(
    driverId: String,
    vehicleId: String,
    onStart: suspend (Double, LocationSnapshot, String) -> Unit,
    onCancel: () -> Unit
) {
    val context = LocalContext.current
    val owner = LocalLifecycleOwner.current
    var odometer by remember { mutableStateOf("") }
    var imageCapture by remember { mutableStateOf<ImageCapture?>(null) }
    var photoPath by remember { mutableStateOf<String?>(null) }
    var gps by remember { mutableStateOf<LocationSnapshot?>(null) }
    var status by remember { mutableStateOf("Capture odometer photo and GPS") }
    var busy by remember { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { grants ->
        if (grants[Manifest.permission.CAMERA] == true || ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            CameraPreviewController(context).bind(owner, PreviewView(context), onReady = { imageCapture = it })
        }
    }

    LaunchedEffect(Unit) {
        permissionLauncher.launch(arrayOf(Manifest.permission.CAMERA, Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
        FusedLocationProvider(context).currentLocation { location, error ->
            gps = location
            status = error ?: if (location?.isUsable() == true) "GPS ready • ±${location.accuracyMeters.toInt()} m" else "GPS accuracy needs review"
        }
    }

    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("START SESSION")
        OutlinedTextField(value = odometer, onValueChange = { odometer = it }, label = { Text("Starting odometer") }, modifier = Modifier.fillMaxWidth())
        AndroidView(factory = { PreviewView(it) }, modifier = Modifier.fillMaxWidth().height(280.dp)) { view ->
            CameraPreviewController(context).bind(owner, view) { imageCapture = it }
        }
        Button(enabled = imageCapture != null && !busy, onClick = {
            val capture = imageCapture ?: return@Button
            CameraCapture(context).capture(capture, "start_odo") { result ->
                result.onSuccess { uri -> photoPath = uri.toString(); status = "Photo captured" }
                    .onFailure { status = "Camera failed: ${it.message}" }
            }
        }, modifier = Modifier.fillMaxWidth()) { Text("CAPTURE ODOMETER") }
        Text(status)
        photoPath?.let { Text("Photo ready") }
        Button(enabled = !busy && odometer.toDoubleOrNull() != null && photoPath != null && gps?.isUsable() == true, onClick = {
            val odo = odometer.toDoubleOrNull() ?: return@Button
            val location = gps ?: return@Button
            busy = true
            LaunchedEffectKey.start(context, odo, location, photoPath!!, driverId, vehicleId, onStart) { busy = false }
        }, modifier = Modifier.fillMaxWidth()) { Text("OPEN SESSION") }
        Button(enabled = !busy, onClick = onCancel, modifier = Modifier.fillMaxWidth()) { Text("CANCEL") }
    }
}

private object LaunchedEffectKey {
    fun start(context: android.content.Context, odo: Double, location: LocationSnapshot, photo: String, driverId: String, vehicleId: String, onStart: suspend (Double, LocationSnapshot, String) -> Unit, done: () -> Unit) {
        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Main).launch {
            try { onStart(odo, location, photo) } finally { done() }
        }
    }
}
