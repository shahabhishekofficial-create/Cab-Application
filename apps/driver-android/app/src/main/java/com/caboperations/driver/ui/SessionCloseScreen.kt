package com.caboperations.driver.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.Settings
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.caboperations.driver.capture.CameraCapture
import com.caboperations.driver.capture.CameraPreviewController
import com.caboperations.driver.data.SessionCloseLocalRepository
import com.caboperations.driver.location.FusedLocationProvider
import com.caboperations.driver.location.LocationSnapshot
import com.caboperations.driver.ocr.OdometerOcrEngine
import com.caboperations.driver.ocr.OdometerOcrResult
import com.caboperations.driver.ocr.OdometerVerifier
import kotlinx.coroutines.launch

@Composable
fun SessionCloseScreen(sessionId: String, driverId: String, vehicleId: String, startOdometer: Double, onClosed: () -> Unit, onCancel: () -> Unit) {
    val context = LocalContext.current
    val owner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    val repository = remember { SessionCloseLocalRepository(context) }
    var odometer by remember { mutableStateOf("") }
    var tripCount by remember { mutableStateOf("") }
    var income by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }
    var imageCapture by remember { mutableStateOf<ImageCapture?>(null) }
    var photoPath by remember { mutableStateOf<String?>(null) }
    var gps by remember { mutableStateOf<LocationSnapshot?>(null) }
    var ocr by remember { mutableStateOf<OdometerOcrResult?>(null) }
    var status by remember { mutableStateOf("Take a clear photo of the closing odometer.") }
    var busy by remember { mutableStateOf(false) }
    var captureComplete by remember { mutableStateOf(false) }

    fun captureGps() {
        val provider = FusedLocationProvider(context)
        if (!provider.isLocationEnabled()) {
            gps = null
            status = "Location services are OFF. Turn on Location to continue."
            runCatching { context.startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)) }
            return
        }
        status = "Getting your GPS location…"
        provider.currentLocation { location, error ->
            gps = location
            status = when {
                error != null -> error
                location?.isUsable() == true -> "Location ready. Finish the closing details."
                location != null -> "Location found, but accuracy needs review."
                else -> "Unable to get a usable GPS location."
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

    DisposableEffect(owner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                val locationGranted = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED || ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
                if (locationGranted) captureGps()
            }
        }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }

    val closeOdo = odometer.toDoubleOrNull()
    val runningKm = closeOdo?.let { it - startOdometer }
    val count = tripCount.toIntOrNull()
    val reportedIncome = income.toDoubleOrNull()
    val verification = if (closeOdo != null && ocr != null) OdometerVerifier.compare(closeOdo, ocr!!.reading, ocr!!.confidence) else null
    val canClose = !busy && closeOdo != null && closeOdo >= startOdometer && count != null && count >= 0 && reportedIncome != null && reportedIncome >= 0 && photoPath != null && gps?.isUsable() == true && ocr != null

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        CabHeader("Finish your session", "${vehicleId} • session summary", onBack = onCancel)
        Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            CabCard(color = CabPurpleSoft) {
                Text("Almost done", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text("Capture the final odometer and a few numbers so your day can be reconciled correctly.", color = CabGray)
                Text("Started at ${"%.0f".format(startOdometer)} km", color = CabPurple, fontWeight = FontWeight.SemiBold)
            }
            CabStep("1", "Closing odometer photo", captureComplete) {
                AndroidView(factory = { PreviewView(it) }, modifier = Modifier.fillMaxWidth().height(250.dp).clip(RoundedCornerShape(16.dp)), update = { view -> CameraPreviewController(context).bind(owner, view) { imageCapture = it } })
                CabPrimaryButton(if (busy) "Reading odometer…" else "Take photo & read", enabled = imageCapture != null && !busy) {
                    val capture = imageCapture ?: return@CabPrimaryButton
                    busy = true; status = "Taking photo…"
                    CameraCapture(context).capture(capture, "close_odo") { result -> result.onSuccess { uri -> photoPath = uri.toString(); scope.launch { status = "Reading odometer…"; ocr = OdometerOcrEngine(context).recognize(uri); captureComplete = true; busy = false; captureGps() } }.onFailure { status = "Camera failed: ${it.message ?: "unknown error"}. Try again."; busy = false } }
                }
            }
            if (captureComplete) CabCard(color = CabGreenSoft) {
                Text("Photo captured ✓", color = CabGreen, fontWeight = FontWeight.Bold)
                Text("You can retake it if the dashboard wasn’t clear.", color = CabGray)
                CabSecondaryButton("Retake photo", enabled = !busy) { captureComplete = false; ocr = null; photoPath = null; status = "Take a clear photo of the closing odometer." }
            }
            CabStep("2", "Mandatory GPS location", gps?.isUsable() == true) {
                Text(
                    when {
                        gps?.isUsable() == true -> "GPS captured. Accuracy ±${gps!!.accuracyMeters.toInt()} m"
                        gps != null -> "GPS is available but accuracy is not sufficient yet."
                        else -> "GPS is required. Location services must be turned on before you can continue."
                    },
                    color = CabGray
                )
            }
            if (gps?.isUsable() == true) CabCard(color = CabGreenSoft) {
                Text("Location ready ✓", color = CabGreen, fontWeight = FontWeight.Bold)
                Text("Accuracy ±${gps!!.accuracyMeters.toInt()} m", color = CabGray)
            }

            CabCard {
                CabSectionLabel("CLOSING DETAILS")
                OutlinedTextField(odometer, { odometer = it }, label = { Text("Closing odometer (km)") }, supportingText = { Text("Enter the number shown on the dashboard") }, modifier = Modifier.fillMaxWidth(), singleLine = true, shape = RoundedCornerShape(14.dp))
                if (closeOdo != null && closeOdo < startOdometer) Text("Closing odometer cannot be below ${"%.0f".format(startOdometer)} km", color = MaterialTheme.colorScheme.error)
                if (runningKm != null && runningKm >= 0) Surface(shape = RoundedCornerShape(12.dp), color = CabPurpleSoft) { Text("Distance today  •  ${"%.1f".format(runningKm)} km", modifier = Modifier.fillMaxWidth().padding(12.dp), color = CabPurple, fontWeight = FontWeight.Bold) }
                ocr?.let { result ->
                    Text(if (result.reading != null) "Camera read ${result.reading} km • ${(result.confidence * 100).toInt()}% confidence" else "Camera couldn’t read the number. Enter it manually; the photo will be reviewed.", color = CabGray)
                    verification?.let { Text("Odometer check: ${it.name}", color = if (it == OdometerVerifier.Decision.PASS) CabGreen else CabAmber, fontWeight = FontWeight.SemiBold) }
                }
                OutlinedTextField(tripCount, { tripCount = it.filter(Char::isDigit) }, label = { Text("Trips completed") }, modifier = Modifier.fillMaxWidth(), singleLine = true, shape = RoundedCornerShape(14.dp))
                OutlinedTextField(income, { income = it }, label = { Text("Total income (₹)") }, modifier = Modifier.fillMaxWidth(), singleLine = true, shape = RoundedCornerShape(14.dp))
                OutlinedTextField(notes, { notes = it }, label = { Text("Notes (optional)") }, modifier = Modifier.fillMaxWidth(), minLines = 2, shape = RoundedCornerShape(14.dp))
            }
            Surface(shape = RoundedCornerShape(14.dp), color = if (canClose) CabGreenSoft else CabAmberSoft) { Text(status, modifier = Modifier.fillMaxWidth().padding(13.dp), color = if (canClose) CabGreen else CabAmber) }
            CabPrimaryButton(if (busy) "Saving…" else "Finish & Close Session", enabled = canClose) {
                val odo = closeOdo ?: return@CabPrimaryButton; val trips = count ?: return@CabPrimaryButton; val amount = reportedIncome ?: return@CabPrimaryButton; val location = gps ?: return@CabPrimaryButton; val photo = photoPath ?: return@CabPrimaryButton; val ocrResult = ocr ?: return@CabPrimaryButton
                val decision = OdometerVerifier.compare(odo, ocrResult.reading, ocrResult.confidence); busy = true; status = "Saving securely on this phone…"
                scope.launch { try { repository.queueCloseSession(sessionId, driverId, vehicleId, odo, location.latitude, location.longitude, location.accuracyMeters, java.time.Instant.ofEpochMilli(location.capturedAtEpochMs).toString(), photo, trips, amount, notes.ifBlank { null }, ocrResult, decision); status = "Session closed locally • sync queued"; onClosed() } catch (e: Exception) { status = e.message ?: "Unable to close. Your photo remains on the phone."; busy = false } }
            }
            CabSecondaryButton("Cancel", enabled = !busy, onClick = onCancel)
        }
    }
}
