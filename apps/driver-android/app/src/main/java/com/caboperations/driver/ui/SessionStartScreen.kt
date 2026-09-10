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
import com.caboperations.driver.ocr.OdometerOcrEngine
import com.caboperations.driver.ocr.OdometerOcrResult
import com.caboperations.driver.ocr.OdometerVerifier
import kotlinx.coroutines.launch

@Composable
fun SessionStartScreen(
    driverId: String,
    vehicleId: String,
    onStart: suspend (Double, LocationSnapshot, String, OdometerOcrResult, OdometerVerifier.Decision) -> Unit,
    onCancel: () -> Unit
) {
    val context = LocalContext.current
    val owner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    var odometer by remember { mutableStateOf("") }
    var imageCapture by remember { mutableStateOf<ImageCapture?>(null) }
    var photoPath by remember { mutableStateOf<String?>(null) }
    var gps by remember { mutableStateOf<LocationSnapshot?>(null) }
    var ocr by remember { mutableStateOf<OdometerOcrResult?>(null) }
    var status by remember { mutableStateOf("Capture odometer photo and GPS") }
    var busy by remember { mutableStateOf(false) }

    fun captureGps() {
        status = "Getting best available GPS fix…"
        FusedLocationProvider(context).currentLocation { location, error ->
            gps = location
            status = when {
                error != null -> error
                location?.isUsable() == true -> "GPS ready • ±${location.accuracyMeters.toInt()} m"
                location != null -> "GPS captured • ±${location.accuracyMeters.toInt()} m • accuracy needs review"
                else -> "GPS unavailable • retry"
            }
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { grants ->
        val cameraGranted = grants[Manifest.permission.CAMERA] == true || ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        val locationGranted = grants[Manifest.permission.ACCESS_FINE_LOCATION] == true || grants[Manifest.permission.ACCESS_COARSE_LOCATION] == true || ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED || ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        if (!cameraGranted) status = "Camera permission is required"
        if (locationGranted) captureGps() else status = "Location permission is required"
    }

    LaunchedEffect(Unit) {
        val cameraGranted = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        val locationGranted = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED || ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        if (!cameraGranted || !locationGranted) permissionLauncher.launch(arrayOf(Manifest.permission.CAMERA, Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)) else captureGps()
    }

    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("START SESSION")
        Text("Driver: $driverId")
        Text("Vehicle: $vehicleId")
        OutlinedTextField(value = odometer, onValueChange = { odometer = it }, label = { Text("Starting odometer") }, modifier = Modifier.fillMaxWidth())
        AndroidView(factory = { PreviewView(it) }, modifier = Modifier.fillMaxWidth().height(280.dp), update = { view -> CameraPreviewController(context).bind(owner, view) { imageCapture = it } })
        Button(enabled = imageCapture != null && !busy, onClick = {
            val capture = imageCapture ?: return@Button
            busy = true
            CameraCapture(context).capture(capture, "start_odo") { result ->
                result.onSuccess { uri ->
                    photoPath = uri.toString()
                    scope.launch {
                        status = "Reading odometer…"
                        ocr = OdometerOcrEngine(context).recognize(uri)
                        status = ocr?.reading?.let { reading -> "OCR: $reading • quality ${(ocr!!.confidence * 100).toInt()}%" } ?: "OCR could not read the odometer • manual review"
                        busy = false
                    }
                }.onFailure { status = "Camera failed: ${it.message}"; busy = false }
            }
        }, modifier = Modifier.fillMaxWidth()) { Text("CAPTURE & READ ODOMETER") }
        Button(enabled = !busy, onClick = { captureGps() }, modifier = Modifier.fillMaxWidth()) { Text("REFRESH GPS") }
        Text(status)
        ocr?.let { result ->
            val manual = odometer.toDoubleOrNull()
            if (manual != null) Text("OCR verification: ${OdometerVerifier.compare(manual, result.reading, result.confidence).name}")
        }
        Button(enabled = !busy && odometer.toDoubleOrNull() != null && photoPath != null && gps?.isUsable() == true && ocr != null, onClick = {
            val odo = odometer.toDoubleOrNull() ?: return@Button
            val location = gps ?: return@Button
            val photo = photoPath ?: return@Button
            val ocrResult = ocr ?: return@Button
            val decision = OdometerVerifier.compare(odo, ocrResult.reading, ocrResult.confidence)
            busy = true
            scope.launch { try { onStart(odo, location, photo, ocrResult, decision) } catch (e: Exception) { status = e.message ?: "Unable to open session"; busy = false } }
        }, modifier = Modifier.fillMaxWidth()) { Text(if (busy) "OPENING…" else "OPEN SESSION") }
        Button(enabled = !busy, onClick = onCancel, modifier = Modifier.fillMaxWidth()) { Text("CANCEL") }
    }
}
