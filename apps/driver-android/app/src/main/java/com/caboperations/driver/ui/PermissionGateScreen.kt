package com.caboperations.driver.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner

private fun cameraGranted(context: android.content.Context) =
    ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED

private fun locationGranted(context: android.content.Context) =
    ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

@Composable
fun PermissionGateScreen(onReady: () -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var camera by remember { mutableStateOf(cameraGranted(context)) }
    var location by remember { mutableStateOf(locationGranted(context)) }
    var message by remember { mutableStateOf("") }
    val allGranted = camera && location

    fun refresh() {
        camera = cameraGranted(context)
        location = locationGranted(context)
    }

    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        refresh()
        message = if (camera && location) "All required permissions granted." else "Allow the remaining permission(s) to continue."
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_RESUME) refresh() }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Column(
        Modifier.fillMaxSize().padding(horizontal = 18.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        CabHeader("Permissions required", "Required before starting a cab session", onBack = null)
        CabCard {
            Text("Two permissions keep your session evidence complete.", style = MaterialTheme.typography.titleMedium, color = CabInk)
            PermissionRow("Camera", "Opening and closing odometer photos", camera)
            PermissionRow("Location", "GPS position and accuracy for session records", location)
        }
        if (!allGranted) {
            CabPrimaryButton("ALLOW REQUIRED PERMISSIONS") {
                launcher.launch(arrayOf(Manifest.permission.CAMERA, Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
            }
            CabSecondaryButton("OPEN APP SETTINGS") {
                context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = Uri.parse("package:${context.packageName}")
                })
            }
        } else {
            CabPrimaryButton("CONTINUE") { onReady() }
        }
        if (message.isNotBlank()) {
            Surface(shape = RoundedCornerShape(14.dp), color = if (allGranted) CabGreenSoft else CabAmberSoft) {
                Text(message, Modifier.fillMaxWidth().padding(13.dp), color = if (allGranted) CabGreen else CabAmber)
            }
        }
    }
}

@Composable
private fun PermissionRow(title: String, description: String, granted: Boolean) {
    Surface(shape = RoundedCornerShape(16.dp), color = if (granted) CabGreenSoft else CabSurface) {
        Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(title, style = MaterialTheme.typography.titleSmall, color = CabInk)
                Text(description, style = MaterialTheme.typography.bodySmall, color = CabGray)
            }
            Text(if (granted) "GRANTED" else "NEEDED", color = if (granted) CabGreen else CabAmber, style = MaterialTheme.typography.labelSmall)
        }
    }
}
