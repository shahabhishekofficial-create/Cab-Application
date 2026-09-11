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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
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
    var status by remember { mutableStateOf("Step 1 of 3: capture the starting odometer") }
    var busy by remember { mutableStateOf(false) }
    var captureComplete by remember { mutableStateOf(false) }

    fun captureGps() {
        status = if (captureComplete) "Step 2 of 3: getting GPS fix…" else "Getting best available GPS fix…"
        FusedLocationProvider(context).currentLocation { location, error ->
            gps = location
            status = when {
                error != null -> error
                location?.isUsable() == true -> if (captureComplete) "GPS ready • Step 3: review and open session" else "GPS ready • capture odometer photo"
                location != null -> "GPS captured • ±${location.accuracyMeters.toInt()} m • accuracy needs review"
                else -> "GPS unavailable • tap REFRESH GPS"
            }
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { grants ->
        val cameraGranted = grants[Manifest.permission.CAMERA] == true || ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        val locationGranted = grants[Manifest.permission.ACCESS_FINE_LOCATION] == true || grants[Manifest.permission.ACCESS_COARSE_LOCATION] == true || ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED || ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        when {
            !cameraGranted && !locationGranted -> status = "Camera and location permissions are required"
            !cameraGranted -> status = "Camera permission is required"
            !locationGranted -> status = "Location permission is required"
            else -> captureGps()
        }
    }

    LaunchedEffect(Unit) {
        val cameraGranted = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        val locationGranted = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED || ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        if (!cameraGranted || !locationGranted) permissionLauncher.launch(arrayOf(Manifest.permission.CAMERA, Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)) else captureGps()
    }

    val manual = odometer.toDoubleOrNull()
    val verification = if (manual != null && ocr != null) OdometerVerifier.compare(manual, ocr!!.reading, ocr!!.confidence) else null
    val canOpen = !busy && manual != null && manual >= 0 && photoPath != null && gps?.isUsable() == true && ocr != null

    Column(modifier = Modifier.fillMaxWidth().padding(16.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("START SESSION")
        Text("Vehicle: $vehicleId")
        Text("Driver: $driverId")
        Text("1. Odometer photo  ✓".takeIf { captureComplete } ?: "1. Odometer photo  • required")
        if (!captureComplete) {
            AndroidView(factory = { PreviewView(it) }, modifier = Modifier.fillMaxWidth().height(260.dp), update = { view -> CameraPreviewController(context).bind(owner, view) { imageCapture = it } })
            Button(enabled = imageCapture != null && !busy, onClick = {
                val capture = imageCapture ?: return@Button
                busy = true
                status = "Capturing photo…"
                CameraCapture(context).capture(capture, "start_odo") { result ->
                    result.onSuccess { uri ->
                        photoPath = uri.toString()
                        scope.launch {
                            status = "Reading odometer…"
                            val resultOcr = OdometerOcrEngine(context).recognize(uri)
                            ocr = resultOcr
                            captureComplete = true
                            busy = false
                            captureGps()
                        }
                    }.onFailure { status = "Camera failed: ${it.message ?: "unknown error"}. Try again."; busy = false }
                }
            }, modifier = Modifier.fillMaxWidth()) { Text(if (busy) "CAPTURING…" else "CAPTURE & READ ODOMETER") }
        } else {
            Text("Photo captured and stored safely on the device.")
            Button(enabled = !busy, onClick = { captureComplete = false; ocr = null; photoPath = null; status = "Step 1 of 3: capture the starting odometer" }, modifier = Modifier.fillMaxWidth()) { Text("RETAKE PHOTO") }
        }

        Text("2. GPS  ${if (gps?.isUsable() == true) "✓ READY" else "• REQUIRED"}")
        if (gps?.isUsable() == true) Text("GPS accuracy: ±${gps!!.accuracyMeters.toInt()} m")
        Button(enabled = !busy, onClick = { captureGps() }, modifier = Modifier.fillMaxWidth()) { Text("REFRESH GPS") }

        Text("3. Confirm starting odometer")
        OutlinedTextField(value = odometer, onValueChange = { odometer = it }, label = { Text("Starting odometer (km)") }, supportingText = { Text("Enter the dashboard reading shown in the photo") }, modifier = Modifier.fillMaxWidth())
        ocr?.let { result ->
            Text(if (result.reading != null) "OCR reading: ${result.reading} • quality ${(result.confidence * 100).toInt()}%" else "OCR could not read the number. Enter it manually; the photo will be marked for review.")
            verification?.let { Text("OCR verification: ${it.name}") }
        }
        if (manual != null && manual < 0) Text("Starting odometer cannot be negative")
        Text(status)
        Button(enabled = canOpen, onClick = {
            val odo = manual ?: return@Button
            val location = gps ?: return@Button
            val photo = photoPath ?: return@Button
            val ocrResult = ocr ?: return@Button
            val decision = OdometerVerifier.compare(odo, ocrResult.reading, ocrResult.confidence)
            busy = true
            status = "Saving session locally…"
            scope.launch {
                try {
                    onStart(odo, location, photo, ocrResult, decision)
                } catch (e: Exception) {
                    status = e.message ?: "Unable to open session. Your photo remains on the device; try again."
                    busy = false
                }
            }
        }, modifier = Modifier.fillMaxWidth()) { Text(if (busy) "OPENING…" else "OPEN SESSION") }
        OutlinedButton(enabled = !busy, onClick = onCancel, modifier = Modifier.fillMaxWidth()) { Text("CANCEL") }
    }
}
