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
import com.caboperations.driver.data.SessionCloseLocalRepository
import com.caboperations.driver.location.FusedLocationProvider
import com.caboperations.driver.location.LocationSnapshot
import kotlinx.coroutines.launch

@Composable
fun SessionCloseScreen(
    sessionId: String,
    driverId: String,
    vehicleId: String,
    startOdometer: Double,
    onClosed: () -> Unit,
    onCancel: () -> Unit
) {
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
    var status by remember { mutableStateOf("Capture closing odometer photo and GPS") }
    var busy by remember { mutableStateOf(false) }

    fun captureGps() {
        FusedLocationProvider(context).currentLocation { location, error ->
            gps = location
            status = error ?: if (location?.isUsable() == true) "GPS ready • ±${location.accuracyMeters.toInt()} m" else "GPS accuracy needs review"
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { grants ->
        val cameraGranted = grants[Manifest.permission.CAMERA] == true || ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        val locationGranted = grants[Manifest.permission.ACCESS_FINE_LOCATION] == true || grants[Manifest.permission.ACCESS_COARSE_LOCATION] == true || ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED || ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        if (!cameraGranted) status = "Camera permission is required"
        if (locationGranted) captureGps() else if (!cameraGranted) status = "Camera and location permissions are required"
    }

    LaunchedEffect(Unit) {
        val cameraGranted = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        val locationGranted = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED || ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        if (!cameraGranted || !locationGranted) permissionLauncher.launch(arrayOf(Manifest.permission.CAMERA, Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)) else captureGps()
    }

    val closeOdo = odometer.toDoubleOrNull()
    val runningKm = closeOdo?.let { it - startOdometer }
    val count = tripCount.toIntOrNull()
    val reportedIncome = income.toDoubleOrNull()

    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("CLOSE SESSION")
        Text("Session: $sessionId")
        Text("Starting odometer: $startOdometer")
        OutlinedTextField(value = odometer, onValueChange = { odometer = it }, label = { Text("Closing odometer") }, modifier = Modifier.fillMaxWidth())
        if (runningKm != null) Text("Running KM: ${"%.2f".format(runningKm)}")
        AndroidView(factory = { PreviewView(it) }, modifier = Modifier.fillMaxWidth().height(260.dp), update = { view -> CameraPreviewController(context).bind(owner, view) { imageCapture = it } })
        Button(enabled = imageCapture != null && !busy, onClick = {
            CameraCapture(context).capture(imageCapture!!, "close_odo") { result ->
                result.onSuccess { uri -> photoPath = uri.toString(); status = "Closing odometer photo captured" }.onFailure { status = "Camera failed: ${it.message}" }
            }
        }, modifier = Modifier.fillMaxWidth()) { Text("CAPTURE CLOSING ODOMETER") }
        OutlinedTextField(value = tripCount, onValueChange = { tripCount = it.filter(Char::isDigit) }, label = { Text("Reported trip count") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(value = income, onValueChange = { income = it }, label = { Text("Reported income (₹)") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(value = notes, onValueChange = { notes = it }, label = { Text("Notes") }, modifier = Modifier.fillMaxWidth())
        Text(status)
        if (gps?.isUsable() == true) Text("GPS ready • ±${gps!!.accuracyMeters.toInt()} m")
        Button(enabled = !busy && closeOdo != null && closeOdo >= startOdometer && count != null && count >= 0 && reportedIncome != null && reportedIncome >= 0 && photoPath != null && gps?.isUsable() == true, onClick = {
            val odo = closeOdo ?: return@Button
            val trips = count ?: return@Button
            val amount = reportedIncome ?: return@Button
            val location = gps ?: return@Button
            val photo = photoPath ?: return@Button
            busy = true
            scope.launch {
                try {
                    repository.queueCloseSession(sessionId, driverId, vehicleId, odo, location.latitude, location.longitude, location.accuracyMeters, java.time.Instant.ofEpochMilli(location.capturedAtEpochMs).toString(), photo, trips, amount, notes.ifBlank { null })
                    status = "Session closed locally • photo queued • pending sync"
                    onClosed()
                } catch (e: Exception) { status = e.message ?: "Unable to close session" } finally { busy = false }
            }
        }, modifier = Modifier.fillMaxWidth()) { Text(if (busy) "CLOSING…" else "CLOSE SESSION") }
        Button(enabled = !busy, onClick = onCancel, modifier = Modifier.fillMaxWidth()) { Text("CANCEL") }
    }
}
