package com.caboperations.driver.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.caboperations.driver.BuildConfig
import com.caboperations.driver.auth.AuthRepository
import com.caboperations.driver.auth.DriverContextRepository
import com.caboperations.driver.data.CabDatabase
import com.caboperations.driver.data.DriverIdentity
import com.caboperations.driver.data.SessionLocalRepository
import com.caboperations.driver.data.SessionStateRepository
import com.caboperations.driver.data.SyncScheduler
import com.caboperations.driver.location.LocationSnapshot
import com.caboperations.driver.ocr.OdometerOcrResult
import com.caboperations.driver.ocr.OdometerVerifier
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant

@Composable
fun DriverApp() {
    val context = LocalContext.current
    val identity = remember { DriverIdentity(context) }
    val auth = remember { AuthRepository(context) }
    val driverContext = remember { DriverContextRepository() }
    val sessionState = remember { SessionStateRepository(context) }
    val localRepository = remember { SessionLocalRepository(context) }
    var currentSession by remember { mutableStateOf(sessionState.current()) }
    var pendingCount by remember { mutableStateOf(0) }
    var screen by remember { mutableStateOf("LOADING") }
    var entryType by remember { mutableStateOf("TRIP") }
    var status by remember { mutableStateOf("") }
    var displayName by remember { mutableStateOf("") }
    var registration by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()

    fun refreshPending() { scope.launch { pendingCount = runCatching { CabDatabase.get(context).pendingTransactionDao().pendingCount() }.getOrDefault(0) } }

    suspend fun loadAuthenticatedDriver(): Boolean {
        val session = auth.session() ?: return false
        val result = withContext(Dispatchers.IO) { driverContext.load(session.accessToken) }
        if (result.isFailure) return false
        val c = result.getOrThrow()
        identity.configure(c.driverId, c.vehicleId ?: return false)
        displayName = c.displayName
        registration = c.registrationNumber.orEmpty()
        return true
    }

    LaunchedEffect(Unit) {
        if (loadAuthenticatedDriver()) screen = "HOME" else { auth.logout(); identity.clearAssignment(); screen = "LOGIN" }
    }
    LaunchedEffect(screen) { refreshPending() }

    MaterialTheme {
        when {
            screen == "LOADING" -> Box(Modifier.fillMaxSize().padding(24.dp)) { CircularProgressIndicator() }
            screen == "LOGIN" -> LoginScreen(auth) {
                scope.launch {
                    if (loadAuthenticatedDriver()) { status = "Logged in • assignment loaded"; screen = "HOME" }
                    else { auth.logout(); identity.clearAssignment(); status = "Login succeeded but no active driver assignment" }
                }
            }
            screen == "START" && identity.driverId != null && identity.vehicleId != null -> SessionStartScreen(
                driverId = identity.driverId!!, vehicleId = identity.vehicleId!!,
                onStart = { odo: Double, gps: LocationSnapshot, photo: String, ocr: OdometerOcrResult, decision: OdometerVerifier.Decision ->
                    val driverId = identity.driverId!!; val vehicleId = identity.vehicleId!!
                    val sessionId = localRepository.queueStartSession(driverId, vehicleId, identity.deviceId, odo, gps.latitude, gps.longitude, gps.accuracyMeters, Instant.ofEpochMilli(gps.capturedAtEpochMs).toString(), photo, ocr, decision)
                    currentSession = sessionState.open(sessionId, driverId, vehicleId, odo)
                    refreshPending(); SyncScheduler.enqueue(context, BuildConfig.API_BASE_URL)
                    status = if (decision == OdometerVerifier.Decision.PASS) "Session saved locally • OCR PASS" else "Session saved locally • OCR REVIEW"
                    screen = "HOME"
                }, onCancel = { screen = "HOME" }
            )
            screen == "CLOSE" && currentSession != null && identity.driverId != null && identity.vehicleId != null -> SessionCloseScreen(
                sessionId = currentSession!!.sessionId, driverId = identity.driverId!!, vehicleId = identity.vehicleId!!, startOdometer = currentSession!!.startOdometer,
                onClosed = { sessionState.close(); currentSession = null; status = "Session close saved locally"; refreshPending(); SyncScheduler.enqueue(context, BuildConfig.API_BASE_URL); screen = "HOME" },
                onCancel = { screen = "HOME" }
            )
            screen == "ENTRY" && currentSession != null && identity.driverId != null && identity.vehicleId != null -> TransactionEntryScreen(
                type = entryType, sessionId = currentSession!!.sessionId, driverId = identity.driverId!!, vehicleId = identity.vehicleId!!,
                onSaved = { status = "${entryType.replaceFirstChar { it.uppercase() }} saved offline"; refreshPending(); SyncScheduler.enqueue(context, BuildConfig.API_BASE_URL); screen = "HOME" }, onCancel = { screen = "HOME" }
            )
            else -> Column(Modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text("Cab Driver", style = MaterialTheme.typography.headlineMedium)
                if (identity.driverId == null || identity.vehicleId == null) Text("No active vehicle assignment. Contact admin.") else {
                    if (displayName.isNotBlank()) Text(displayName)
                    if (registration.isNotBlank()) Text("Vehicle: $registration")
                    Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) { Text("SESSION", style = MaterialTheme.typography.labelLarge); Text(if (currentSession != null) "OPEN" else "NOT STARTED", style = MaterialTheme.typography.titleLarge); currentSession?.let { Text("Session: ${it.sessionId}") }; Text("Pending sync: $pendingCount") } }
                    if (currentSession == null) Button({ screen = "START" }, Modifier.fillMaxWidth()) { Text("START SESSION") } else {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) { Button({ entryType = "TRIP"; screen = "ENTRY" }, Modifier.weight(1f)) { Text("ADD TRIP") }; Button({ entryType = "FUEL"; screen = "ENTRY" }, Modifier.weight(1f)) { Text("FUEL") } }
                        OutlinedButton({ entryType = "EXPENSE"; screen = "ENTRY" }, Modifier.fillMaxWidth()) { Text("EXPENSE") }
                        OutlinedButton({ screen = "CLOSE" }, Modifier.fillMaxWidth()) { Text("CLOSE SESSION") }
                    }
                }
                if (status.isNotBlank()) Text(status)
                Text("Offline-first: entries are saved locally and sync when connectivity returns.")
                TextButton(onClick = { auth.logout(); identity.clearAssignment(); currentSession = null; screen = "LOGIN" }) { Text("LOG OUT") }
            }
        }
    }
}
