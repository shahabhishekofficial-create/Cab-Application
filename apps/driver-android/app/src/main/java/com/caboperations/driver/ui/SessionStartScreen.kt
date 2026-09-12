package com.caboperations.driver.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.ImageCapture
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
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
fun SessionStartScreen(driverId: String, vehicleId: String, onStart: suspend (Double, LocationSnapshot, String, OdometerOcrResult, OdometerVerifier.Decision) -> Unit, onCancel: () -> Unit) {
    val context = LocalContext.current
    val owner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    var odometer by remember { mutableStateOf("") }
    var imageCapture by remember { mutableStateOf<ImageCapture?>(null) }
    var photoPath by remember { mutableStateOf<String?>(null) }
    var gps by remember { mutableStateOf<LocationSnapshot?>(null) }
    var ocr by remember { mutableStateOf<OdometerOcrResult?>(null) }
    var status by remember { mutableStateOf("Take a clear photo of the dashboard odometer.") }
    var busy by remember { mutableStateOf(false) }
    var captureComplete by remember { mutableStateOf(false) }

    fun captureGps() {
        status = "Getting your GPS location…"
        FusedLocationProvider(context).currentLocation { location, error ->
            gps = location
            status = when {
                error != null -> error
                location?.isUsable() == true -> "Location ready. Review the odometer and start."
                location != null -> "Location found, but accuracy needs review."
                else -> "Couldn’t get location. Tap Refresh location."
            }
        }
    }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { grants ->
        val cameraGranted = grants[Manifest.permission.CAMERA] == true || ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        val locationGranted = grants[Manifest.permission.ACCESS_FINE_LOCATION] == true || grants[Manifest.permission.ACCESS_COARSE_LOCATION] == true || ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED || ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        when { !cameraGranted && !locationGranted -> status = "Camera and location permissions are required"; !cameraGranted -> status = "Camera permission is required"; !locationGranted -> status = "Location permission is required"; else -> captureGps() }
    }
    LaunchedEffect(Unit) {
        val cameraGranted = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        val locationGranted = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED || ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        if (!cameraGranted || !locationGranted) permissionLauncher.launch(arrayOf(Manifest.permission.CAMERA, Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)) else captureGps()
    }
    val manual = odometer.toDoubleOrNull()
    val verification = if (manual != null && ocr != null) OdometerVerifier.compare(manual, ocr!!.reading, ocr!!.confidence) else null
    val canOpen = !busy && manual != null && manual >= 0 && photoPath != null && gps?.isUsable() == true && ocr != null

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        CabHeader("Start your session", "${vehicleId} • ready for work", onBack = onCancel)
        Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            CabCard(color = CabPurpleSoft) {
                Text("Let’s get you on the road", style = MaterialTheme.typography.titleLarge, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                Text("We’ll capture the starting odometer and location before opening today’s session.", color = CabGray)
            }
            CabStep("1", "Starting odometer photo", captureComplete) {
                if (!captureComplete) {
                    AndroidView(factory = { PreviewView(it) }, modifier = Modifier.fillMaxWidth().height(250.dp).clip(RoundedCornerShape(16.dp)), update = { view -> CameraPreviewController(context).bind(owner, view) { imageCapture = it } })
                    CabPrimaryButton(if (busy) "Reading odometer…" else "Take photo & read", enabled = imageCapture != null && !busy) {
                        val capture = imageCapture ?: return@CabPrimaryButton
                        busy = true; status = "Taking photo…"
                        CameraCapture(context).capture(capture, "start_odo") { result ->
                            result.onSuccess { uri ->
                                photoPath = uri.toString()
                                scope.launch { status = "Reading odometer…"; ocr = OdometerOcrEngine(context).recognize(uri); captureComplete = true; busy = false; captureGps() }
                            }.onFailure { status = "Camera failed: ${it.message ?: "unknown error"}. Try again."; busy = false }
                        }
                    }
                } else {
                    Text("Photo saved on this phone. You can retake it if the dashboard wasn’t clear.", color = CabGray)
                    CabSecondaryButton("Retake photo", enabled = !busy) { captureComplete = false; ocr = null; photoPath = null; status = "Take a clear photo of the dashboard odometer." }
                }
            }
            CabStep("2", "Current location", gps?.isUsable() == true) {
                Text(if (gps == null) "Waiting for GPS…" else "Accuracy needs improvement before the session can start.", color = CabGray)
                CabSecondaryButton("Refresh location", enabled = !busy, onClick = { captureGps() })
            }
            if (gps?.isUsable() == true) {
                CabCard(color = CabGreenSoft) { Text("Location ready ✓", fontWeight = androidx.compose.ui.text.font.FontWeight.Bold, color = CabGreen); Text("Accuracy ±${gps!!.accuracyMeters.toInt()} m", color = CabGray) }
            }
            CabStep("3", "Confirm starting odometer", false) {
                OutlinedTextField(value = odometer, onValueChange = { odometer = it }, label = { Text("Starting odometer (km)") }, supportingText = { Text("Enter the number shown on the dashboard") }, modifier = Modifier.fillMaxWidth(), singleLine = true, shape = RoundedCornerShape(14.dp))
                ocr?.let { result ->
                    Text(if (result.reading != null) "Camera read ${result.reading} km • ${(result.confidence * 100).toInt()}% confidence" else "The camera couldn’t read the number. Enter it manually; the photo will be reviewed.", color = CabGray)
                    verification?.let { Text("Odometer check: ${it.name}", color = if (it == OdometerVerifier.Decision.PASS) CabGreen else CabAmber, fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold) }
                }
                if (manual != null && manual < 0) Text("Odometer cannot be negative", color = MaterialTheme.colorScheme.error)
            }
            Surface(shape = RoundedCornerShape(14.dp), color = if (canOpen) CabGreenSoft else CabAmberSoft) {
                Text(status, modifier = Modifier.fillMaxWidth().padding(13.dp), color = if (canOpen) CabGreen else CabAmber)
            }
            CabPrimaryButton(if (busy) "Opening session…" else "Start Session", enabled = canOpen) {
                val odo = manual ?: return@CabPrimaryButton; val location = gps ?: return@CabPrimaryButton; val photo = photoPath ?: return@CabPrimaryButton; val ocrResult = ocr ?: return@CabPrimaryButton
                val decision = OdometerVerifier.compare(odo, ocrResult.reading, ocrResult.confidence); busy = true; status = "Saving securely on this phone…"
                scope.launch { try { onStart(odo, location, photo, ocrResult, decision) } catch (e: Exception) { status = e.message ?: "Unable to start. Your photo remains on the phone."; busy = false } }
            }
            CabSecondaryButton("Cancel", enabled = !busy, onClick = onCancel)
        }
    }
}
