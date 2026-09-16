package com.caboperations.driver.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.Camera
import androidx.camera.core.ImageCapture
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import com.caboperations.driver.capture.CameraCapture
import com.caboperations.driver.capture.CameraPreviewController
import com.caboperations.driver.data.CabDatabase
import com.caboperations.driver.location.FusedLocationProvider
import com.caboperations.driver.location.LocationSnapshot
import com.caboperations.driver.ocr.OdometerOcrEngine
import com.caboperations.driver.ocr.OdometerOcrResult
import com.caboperations.driver.ocr.OdometerVerifier
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

private enum class StartStep { ODOMETER, CAMERA, CONFIRM }

@Composable
fun SessionStartScreen(driverId: String, vehicleId: String, onStart: suspend (Double, LocationSnapshot, String, OdometerOcrResult, OdometerVerifier.Decision) -> Unit, onCancel: () -> Unit) {
    val context = LocalContext.current
    val owner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    var step by remember { mutableStateOf(StartStep.ODOMETER) }
    var odometer by remember { mutableStateOf("") }
    var currentOdometer by remember { mutableStateOf<Double?>(null) }
    var imageCapture by remember { mutableStateOf<ImageCapture?>(null) }
    var camera by remember { mutableStateOf<Camera?>(null) }
    var flashOn by remember { mutableStateOf(false) }
    var photoPath by remember { mutableStateOf<String?>(null) }
    var gps by remember { mutableStateOf<LocationSnapshot?>(null) }
    var ocr by remember { mutableStateOf<OdometerOcrResult?>(null) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }

    LaunchedEffect(vehicleId) { currentOdometer = withContext(Dispatchers.IO) { runCatching { CabDatabase.get(context).vehicleDao().currentOdometer(vehicleId) }.getOrNull() } }

    fun captureGps() {
        val provider = FusedLocationProvider(context)
        if (!provider.isLocationEnabled()) { gps = null; error = "Location services are OFF. Turn on Location to continue."; runCatching { context.startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)) }; return }
        provider.currentLocation { location, message -> gps = location; if (message != null && location == null) error = message }
    }

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        val cameraGranted = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        val locationGranted = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED || ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        if (cameraGranted && locationGranted) captureGps()
    }
    LaunchedEffect(Unit) {
        val cameraGranted = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        val locationGranted = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED || ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        if (!cameraGranted || !locationGranted) permissionLauncher.launch(arrayOf(Manifest.permission.CAMERA, Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)) else captureGps()
    }
    DisposableEffect(owner) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_RESUME) { val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED || ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED; if (granted) captureGps() } }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }

    when (step) {
        StartStep.ODOMETER -> OdometerEntryStep(value = odometer, currentOdometer = currentOdometer, error = error, onValueChange = { odometer = it.filter(Char::isDigit).take(9); error = "" }, onNext = {
            when (val validation = OdometerValidation.validateStartingOdometer(odometer, currentOdometer)) {
                is OdometerValidationResult.Valid -> { error = ""; step = StartStep.CAMERA }
                is OdometerValidationResult.Regression -> error = "Entered reading (${validation.entered}) is lower than last recorded reading (${validation.current})"
                else -> error = "Enter a valid odometer reading."
            }
        }, onBack = onCancel)
        StartStep.CAMERA -> CameraCaptureStep(owner = owner, imageCapture = imageCapture, flashOn = flashOn, busy = busy, onCameraReady = { capture, boundCamera -> imageCapture = capture; camera = boundCamera }, onFlash = { enabled -> flashOn = enabled; camera?.cameraControl?.enableTorch(enabled) }, onShutter = {
            val capture = imageCapture ?: return@CameraCaptureStep
            busy = true; error = ""
            CameraCapture(context).capture(capture, "start_odo") { result -> result.onSuccess { path -> if (path.isBlank()) { error = "Camera returned an empty file path."; busy = false; return@onSuccess }; photoPath = path; scope.launch { ocr = withContext(Dispatchers.Default) { OdometerOcrEngine(context).recognize(Uri.fromFile(File(path))) }; busy = false; step = StartStep.CONFIRM; captureGps() } }.onFailure { error = "Camera could not capture the odometer. Try again."; busy = false } }
        }, onManual = { step = StartStep.ODOMETER }, onBack = { step = StartStep.ODOMETER }, error = error)
        StartStep.CONFIRM -> OdometerConfirmationStep(odometer = odometer, ocr = ocr, gps = gps, photoPath = photoPath, busy = busy, error = error, onEdit = { step = StartStep.ODOMETER }, onRetake = { step = StartStep.CAMERA; ocr = null; photoPath = null }, onConfirm = {
            val validation = OdometerValidation.validateStartingOdometer(odometer, currentOdometer)
            val value = (validation as? OdometerValidationResult.Valid)?.value
            val location = gps; val path = photoPath; val result = ocr
            if (value == null || location?.isUsable() != true || path.isNullOrBlank() || result == null) { error = "Complete the odometer, photo and GPS checks before confirming."; return@OdometerConfirmationStep }
            busy = true
            scope.launch(Dispatchers.IO) { try { onStart(value, location, path, result, OdometerVerifier.compare(value, result.reading, result.confidence)) } catch (e: android.database.sqlite.SQLiteException) { error = e.message ?: "Unable to save the session locally."; busy = false } catch (e: Exception) { error = e.message ?: "Unable to start session."; busy = false } }
        }, onBack = { step = StartStep.ODOMETER })
    }
}

@Composable
private fun OdometerEntryStep(value: String, currentOdometer: Double?, error: String, onValueChange: (String) -> Unit, onNext: () -> Unit, onBack: () -> Unit) {
    val focusRequester = androidx.compose.ui.focus.FocusRequester()
    val keyboard = LocalSoftwareKeyboardController.current
    LaunchedEffect(Unit) { focusRequester.requestFocus(); keyboard?.show() }
    Column(Modifier.fillMaxSize().background(Color(0xFF121212))) {
        StartHeader("START SESSION", "Step 1 of 3", onBack)
        Column(Modifier.fillMaxWidth().weight(1f).padding(horizontal = 20.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Text("Current Odometer (km)", color = Color(0xFF9E9E9E), style = MaterialTheme.typography.titleMedium); Spacer(Modifier.height(14.dp))
            OutlinedTextField(value = value, onValueChange = onValueChange, modifier = Modifier.fillMaxWidth().height(100.dp).focusRequester(focusRequester), textStyle = MaterialTheme.typography.displayLarge.copy(fontSize = 64.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, color = Color.White), singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Next), keyboardActions = KeyboardActions(onNext = { onNext() }), label = { Text("Total Vehicle KM") }, placeholder = { Text("Enter Total Vehicle KM") }, isError = error.isNotBlank())
            if (currentOdometer != null) { Spacer(Modifier.height(10.dp)); Text("Last recorded: ${OdometerValidation.format(currentOdometer)} km", color = Color(0xFF9E9E9E)) }
            if (error.isNotBlank()) { Spacer(Modifier.height(10.dp)); Text(error, color = Color(0xFFFF6B6B), fontWeight = FontWeight.SemiBold) }
            Spacer(Modifier.height(24.dp)); Button(onClick = onNext, modifier = Modifier.fillMaxWidth().height(56.dp), enabled = value.isNotBlank(), colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF27AE60))) { Text("NEXT • CAMERA", fontWeight = FontWeight.Bold) }
        }
    }
}

@Composable
private fun StartHeader(title: String, subtitle: String, onBack: () -> Unit) {
    Row(Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) { Text("‹", color = Color.White, fontSize = 38.sp, modifier = Modifier.size(48.dp).clickable(onClick = onBack)); Column(Modifier.weight(1f)) { Text(title, color = Color.White, fontWeight = FontWeight.Bold, letterSpacing = 1.2.sp); Text(subtitle, color = Color(0xFF9E9E9E), style = MaterialTheme.typography.labelMedium) } }
}

@Composable
private fun CameraCaptureStep(owner: LifecycleOwner, imageCapture: ImageCapture?, flashOn: Boolean, busy: Boolean, onCameraReady: (ImageCapture, Camera) -> Unit, onFlash: (Boolean) -> Unit, onShutter: () -> Unit, onManual: () -> Unit, onBack: () -> Unit, error: String) {
    Column(Modifier.fillMaxSize().background(Color.Black)) {
        Box(Modifier.fillMaxWidth().weight(1f)) {
            AndroidView(factory = { viewContext -> PreviewView(viewContext).also { previewView -> CameraPreviewController(viewContext).bindWithCamera(owner, previewView, onCameraReady) } }, modifier = Modifier.fillMaxSize(), update = {})
            Box(Modifier.fillMaxSize().alpha(0.28f).background(Color.Black)); Box(Modifier.fillMaxWidth(0.86f).fillMaxHeight(0.30f).align(Alignment.Center).border(3.dp, Color.White, RoundedCornerShape(12.dp))); Text("ALIGN DASHBOARD ODOMETER", color = Color.White, fontWeight = FontWeight.Bold, modifier = Modifier.align(Alignment.Center).padding(top = 150.dp)); IconButton(onClick = { onFlash(!flashOn) }, modifier = Modifier.align(Alignment.TopEnd).padding(16.dp).size(56.dp).background(Color.Black.copy(alpha = 0.6f), CircleShape)) { Text(if (flashOn) "⚡" else "♢", color = Color.White, fontSize = 25.sp) }; StartHeader("ODOMETER CAMERA", "Step 2 of 3", onBack)
        }
        Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) { if (error.isNotBlank()) Text(error, color = Color(0xFFFF6B6B), fontWeight = FontWeight.SemiBold); Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) { Button(onClick = onManual, modifier = Modifier.height(56.dp), shape = RoundedCornerShape(28.dp), colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF242424))) { Text("Enter Manually") }; Spacer(Modifier.weight(1f)); Box(Modifier.size(100.dp).clip(CircleShape).background(if (busy) Color(0xFF777777) else Color.White).clickable(enabled = !busy && imageCapture != null, onClick = onShutter), contentAlignment = Alignment.Center) { Box(Modifier.size(78.dp).clip(CircleShape).background(Color(0xFFDDDDDD))) } } }
    }
}

@Composable
private fun OdometerConfirmationStep(odometer: String, ocr: OdometerOcrResult?, gps: LocationSnapshot?, photoPath: String?, busy: Boolean, error: String, onEdit: () -> Unit, onRetake: () -> Unit, onConfirm: () -> Unit, onBack: () -> Unit) {
    val ocrValue = ocr?.reading?.let(OdometerValidation::format) ?: "—"
    Column(Modifier.fillMaxSize().background(Color(0xFF121212)).padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        StartHeader("CONFIRM ODOMETER", "Step 3 of 3", onBack); Box(Modifier.fillMaxWidth().height(180.dp).background(Color(0xFF242424), RoundedCornerShape(16.dp)), contentAlignment = Alignment.Center) { Text(if (photoPath.isNullOrBlank()) "Photo not captured" else "Odometer photo saved", color = Color(0xFF9E9E9E)) }; Text("OCR audit reading", color = Color(0xFF9E9E9E)); Text(ocrValue, color = Color.White, fontSize = 52.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace); Text("Manual reading: $odometer km", color = Color.White, fontSize = 22.sp); Text(if (gps?.isUsable() == true) "GPS ready • ±${gps.accuracyMeters.toInt()} m" else "GPS not ready", color = if (gps?.isUsable() == true) Color(0xFF27AE60) else Color(0xFFFFB000)); if (error.isNotBlank()) Text(error, color = Color(0xFFFF6B6B), fontWeight = FontWeight.SemiBold); Spacer(Modifier.weight(1f)); Button(onClick = onConfirm, enabled = !busy, modifier = Modifier.fillMaxWidth().height(56.dp), colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF27AE60))) { Text(if (busy) "STARTING…" else "CONFIRM • START SESSION", fontWeight = FontWeight.Bold) }; OutlinedButton(onClick = onRetake, enabled = !busy, modifier = Modifier.fillMaxWidth().height(56.dp)) { Text("RETAKE PHOTO") }; OutlinedButton(onClick = onEdit, enabled = !busy, modifier = Modifier.fillMaxWidth().height(56.dp)) { Text("EDIT ODOMETER") }; Spacer(Modifier.height(8.dp))
    }
}
