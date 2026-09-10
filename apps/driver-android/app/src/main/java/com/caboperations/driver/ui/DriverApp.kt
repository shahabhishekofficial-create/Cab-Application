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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.caboperations.driver.data.DriverIdentity
import com.caboperations.driver.data.SessionLocalRepository
import com.caboperations.driver.data.SessionStateRepository
import com.caboperations.driver.data.SyncScheduler
import com.caboperations.driver.location.LocationSnapshot
import kotlinx.coroutines.launch

private const val API_BASE_URL = "http://10.0.2.2:3000"

@Composable
fun DriverApp() {
    val context = LocalContext.current
    val identity = remember { DriverIdentity(context) }
    val sessionState = remember { SessionStateRepository(context) }
    val localRepository = remember { SessionLocalRepository(context) }
    val scope = rememberCoroutineScope()

    var currentSession by remember { mutableStateOf(sessionState.current()) }
    var pendingCount by remember { mutableStateOf(0) }
    var showStart by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        pendingCount = runCatching {
            com.caboperations.driver.data.CabDatabase.get(context).pendingTransactionDao().pendingCount()
        }.getOrDefault(0)
    }

    MaterialTheme {
        if (showStart && identity.driverId != null && identity.vehicleId != null) {
            SessionStartScreen(
                driverId = identity.driverId!!,
                vehicleId = identity.vehicleId!!,
                onStart = { odo: Double, gps: LocationSnapshot, photo: String ->
                    val driverId = identity.driverId!!
                    val vehicleId = identity.vehicleId!!
                    val sessionId = localRepository.queueStartSession(
                        driverId = driverId,
                        vehicleId = vehicleId,
                        deviceId = identity.deviceId,
                        startOdometer = odo,
                        startLat = gps.latitude,
                        startLng = gps.longitude,
                        startAccuracyM = gps.accuracyMeters,
                        startGpsAt = gps.capturedAt,
                        startOdometerFileId = null
                    )
                    currentSession = sessionState.open(sessionId, driverId, vehicleId, odo)
                    pendingCount = com.caboperations.driver.data.CabDatabase.get(context).pendingTransactionDao().pendingCount()
                    SyncScheduler.enqueue(context, API_BASE_URL)
                    status = "Session saved locally • waiting for sync"
                    showStart = false
                },
                onCancel = { showStart = false }
            )
            return@MaterialTheme
        }

        Column(
            modifier = Modifier.fillMaxSize().padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text("Cab Driver", style = MaterialTheme.typography.headlineMedium)

            if (identity.driverId == null || identity.vehicleId == null) {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("DRIVER SETUP", style = MaterialTheme.typography.labelLarge)
                        Text("Development identity is not configured.")
                        Text("A driver ID and vehicle ID must be assigned before a session can start.")
                    }
                }
            } else {
                Text("Driver: ${identity.driverId}")
                Text("Vehicle: ${identity.vehicleId}")

                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("SESSION", style = MaterialTheme.typography.labelLarge)
                        Text(if (currentSession != null) "OPEN" else "NOT STARTED", style = MaterialTheme.typography.titleLarge)
                        currentSession?.let { Text("Session: ${it.sessionId}") }
                        Text("Start odometer: ${currentSession?.startOdometer ?: "—"}")
                        Text("Pending sync: $pendingCount")
                    }
                }

                if (currentSession == null) {
                    Button(onClick = { showStart = true }, modifier = Modifier.fillMaxWidth()) {
                        Text("START SESSION")
                    }
                } else {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        Button(onClick = {}, modifier = Modifier.weight(1f)) { Text("ADD TRIP") }
                        Button(onClick = {}, modifier = Modifier.weight(1f)) { Text("FUEL") }
                    }
                    OutlinedButton(onClick = {}, modifier = Modifier.fillMaxWidth()) { Text("EXPENSE") }
                    OutlinedButton(onClick = {}, modifier = Modifier.fillMaxWidth()) { Text("CLOSE SESSION") }
                }
            }

            if (status.isNotBlank()) Text(status)
            Text("Offline-first: entries are saved locally and synced when connectivity returns.")
        }
    }
}
