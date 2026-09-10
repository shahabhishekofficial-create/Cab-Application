package com.caboperations.driver.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.caboperations.driver.data.CabDatabase
import com.caboperations.driver.data.DriverIdentity
import com.caboperations.driver.data.SessionLocalRepository
import com.caboperations.driver.data.SessionStateRepository
import com.caboperations.driver.data.SyncScheduler
import com.caboperations.driver.location.LocationSnapshot
import com.caboperations.driver.ocr.OdometerOcrResult
import com.caboperations.driver.ocr.OdometerVerifier
import java.time.Instant

private const val API_BASE_URL = "http://10.0.2.2:3000"

@Composable
fun DriverApp() {
    val context = LocalContext.current
    val identity = remember { DriverIdentity(context) }
    val sessionState = remember { SessionStateRepository(context) }
    val localRepository = remember { SessionLocalRepository(context) }
    var currentSession by remember { mutableStateOf(sessionState.current()) }
    var pendingCount by remember { mutableStateOf(0) }
    var screen by remember { mutableStateOf("HOME") }
    var entryType by remember { mutableStateOf("TRIP") }
    var status by remember { mutableStateOf("") }

    suspend fun refreshPending() {
        pendingCount = runCatching { CabDatabase.get(context).pendingTransactionDao().pendingCount() }.getOrDefault(0)
    }

    LaunchedEffect(screen) { refreshPending() }

    MaterialTheme {
        if (screen == "START" && identity.driverId != null && identity.vehicleId != null) {
            SessionStartScreen(
                driverId = identity.driverId!!,
                vehicleId = identity.vehicleId!!,
                onStart = { odo: Double, gps: LocationSnapshot, photo: String, ocr: OdometerOcrResult, decision: OdometerVerifier.Decision ->
                    val driverId = identity.driverId!!
                    val vehicleId = identity.vehicleId!!
                    val sessionId = localRepository.queueStartSession(
                        driverId, vehicleId, identity.deviceId, odo,
                        gps.latitude, gps.longitude, gps.accuracyMeters,
                        Instant.ofEpochMilli(gps.capturedAtEpochMs).toString(), photo, ocr, decision
                    )
                    currentSession = sessionState.open(sessionId, driverId, vehicleId, odo)
                    refreshPending()
                    SyncScheduler.enqueue(context, API_BASE_URL)
                    status = if (decision == OdometerVerifier.Decision.PASS) "Session saved locally • OCR PASS • pending sync" else "Session saved locally • OCR REVIEW • pending sync"
                    screen = "HOME"
                },
                onCancel = { screen = "HOME" }
            )
        } else if (screen == "CLOSE" && currentSession != null && identity.driverId != null && identity.vehicleId != null) {
            SessionCloseScreen(
                sessionId = currentSession!!.sessionId,
                driverId = identity.driverId!!,
                vehicleId = identity.vehicleId!!,
                startOdometer = currentSession!!.startOdometer,
                onClosed = {
                    sessionState.close(); currentSession = null
                    status = "Session closed locally • photo queued • pending sync"
                    refreshPending(); SyncScheduler.enqueue(context, API_BASE_URL); screen = "HOME"
                },
                onCancel = { screen = "HOME" }
            )
        } else if (screen == "ENTRY" && currentSession != null && identity.driverId != null && identity.vehicleId != null) {
            TransactionEntryScreen(
                type = entryType, sessionId = currentSession!!.sessionId,
                driverId = identity.driverId!!, vehicleId = identity.vehicleId!!,
                onSaved = { status = "${entryType.replaceFirstChar { it.uppercase() }} saved offline • pending sync"; refreshPending(); SyncScheduler.enqueue(context, API_BASE_URL); screen = "HOME" },
                onCancel = { screen = "HOME" }
            )
        } else {
            Column(modifier = Modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text("Cab Driver", style = MaterialTheme.typography.headlineMedium)
                if (identity.driverId == null || identity.vehicleId == null) {
                    Card(modifier = Modifier.fillMaxWidth()) { Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) { Text("DRIVER SETUP", style = MaterialTheme.typography.labelLarge); Text("Development identity is not configured."); Text("A driver ID and vehicle ID must be assigned before a session can start.") } }
                } else {
                    Text("Driver: ${identity.driverId}"); Text("Vehicle: ${identity.vehicleId}")
                    Card(modifier = Modifier.fillMaxWidth()) { Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) { Text("SESSION", style = MaterialTheme.typography.labelLarge); Text(if (currentSession != null) "OPEN" else "NOT STARTED", style = MaterialTheme.typography.titleLarge); currentSession?.let { Text("Session: ${it.sessionId}") }; Text("Start odometer: ${currentSession?.startOdometer ?: "—"}"); Text("Pending sync: $pendingCount") } }
                    if (currentSession == null) Button(onClick = { screen = "START" }, modifier = Modifier.fillMaxWidth()) { Text("START SESSION") }
                    else {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) { Button(onClick = { entryType = "TRIP"; screen = "ENTRY" }, modifier = Modifier.weight(1f)) { Text("ADD TRIP") }; Button(onClick = { entryType = "FUEL"; screen = "ENTRY" }, modifier = Modifier.weight(1f)) { Text("FUEL") } }
                        OutlinedButton(onClick = { entryType = "EXPENSE"; screen = "ENTRY" }, modifier = Modifier.fillMaxWidth()) { Text("EXPENSE") }
                        OutlinedButton(onClick = { screen = "CLOSE" }, modifier = Modifier.fillMaxWidth()) { Text("CLOSE SESSION") }
                    }
                }
                if (status.isNotBlank()) Text(status)
                Text("Offline-first: entries are saved locally and synced when connectivity returns.")
            }
        }
    }
}
