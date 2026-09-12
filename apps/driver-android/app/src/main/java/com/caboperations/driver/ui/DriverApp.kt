package com.caboperations.driver.ui

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
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

private fun driverPermissionsGranted(context: android.content.Context): Boolean =
    ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED &&
        (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED)

@Composable
fun DriverApp(oauthUri: Uri? = null, onOAuthUriConsumed: () -> Unit = {}) {
    val context = LocalContext.current
    val identity = remember { DriverIdentity(context) }
    val auth = remember { AuthRepository(context) }
    val driverContext = remember { DriverContextRepository() }
    val sessionState = remember { SessionStateRepository(context) }
    val localRepository = remember { SessionLocalRepository(context) }
    var currentSession by remember { mutableStateOf(sessionState.current()) }
    var pendingCount by remember { mutableStateOf(0) }
    var exhaustedCount by remember { mutableStateOf(0) }
    var screen by remember { mutableStateOf("LOADING") }
    var entryType by remember { mutableStateOf("TRIP") }
    var status by remember { mutableStateOf("") }
    var displayName by remember { mutableStateOf("") }
    var registration by remember { mutableStateOf("") }
    var showLogoutConfirm by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    fun authenticatedDestination(): String = if (driverPermissionsGranted(context)) "HOME" else "PERMISSIONS"

    fun refreshPending() {
        scope.launch {
            val dao = CabDatabase.get(context).pendingTransactionDao()
            pendingCount = runCatching { dao.pendingCount() }.getOrDefault(0)
            exhaustedCount = runCatching { dao.exhaustedCount() }.getOrDefault(0)
        }
    }

    fun restoreLocalSessionIfValid() {
        val cached = sessionState.current() ?: return
        if (cached.driverId == identity.driverId && cached.vehicleId == identity.vehicleId) {
            currentSession = cached
        } else {
            sessionState.close()
            currentSession = null
            status = "A stale local session was cleared safely."
        }
    }

    suspend fun loadAuthenticatedDriver(): Boolean {
        val session = auth.session() ?: return false
        val result = withContext(Dispatchers.IO) { driverContext.load(session.accessToken) }
        if (result.isSuccess) {
            val c = result.getOrThrow()
            val vehicleId = c.vehicleId ?: return false
            identity.configure(c.driverId, vehicleId)
            displayName = c.displayName
            registration = c.registrationNumber.orEmpty()
            restoreLocalSessionIfValid()
            return true
        }
        if (identity.driverId != null && identity.vehicleId != null) {
            restoreLocalSessionIfValid()
            status = "Offline mode • server unavailable. Local entries are safe."
            return true
        }
        return false
    }

    LaunchedEffect(oauthUri) {
        val uri = oauthUri ?: return@LaunchedEffect
        onOAuthUriConsumed()
        if (uri.scheme != "cabdriver" || uri.host != "auth-callback") return@LaunchedEffect
        screen = "LOADING"
        val result = withContext(Dispatchers.IO) { auth.consumeGoogleCallback(uri) }
        if (result.isSuccess && loadAuthenticatedDriver()) {
            status = "Google login successful • assignment loaded"
            screen = authenticatedDestination()
        } else {
            auth.logout()
            identity.clearAssignment()
            sessionState.close()
            status = result.exceptionOrNull()?.message ?: "Google login failed"
            screen = "LOGIN"
        }
    }

    LaunchedEffect(Unit) {
        if (oauthUri == null) {
            if (loadAuthenticatedDriver()) screen = authenticatedDestination()
            else { auth.logout(); identity.clearAssignment(); sessionState.close(); screen = "LOGIN" }
        }
    }
    LaunchedEffect(screen) { refreshPending() }

    MaterialTheme {
        when {
            screen == "LOADING" -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    CircularProgressIndicator()
                    Text("Loading Cab Operations…", style = MaterialTheme.typography.bodyMedium)
                }
            }
            screen == "LOGIN" -> LoginScreen(auth) {
                scope.launch {
                    if (loadAuthenticatedDriver()) { status = "Logged in • assignment loaded"; screen = authenticatedDestination() }
                    else { auth.logout(); identity.clearAssignment(); sessionState.close(); status = "Login succeeded but no active driver assignment" }
                }
            }
            screen == "PERMISSIONS" -> PermissionGateScreen(onReady = { screen = "HOME" })
            screen == "START" && identity.driverId != null && identity.vehicleId != null -> SessionStartScreen(
                driverId = identity.driverId!!, vehicleId = identity.vehicleId!!,
                onStart = { odo: Double, gps: LocationSnapshot, photo: String, ocr: OdometerOcrResult, decision: OdometerVerifier.Decision ->
                    val driverId = identity.driverId!!; val vehicleId = identity.vehicleId!!
                    val sessionId = localRepository.queueStartSession(driverId, vehicleId, identity.deviceId, odo, gps.latitude, gps.longitude, gps.accuracyMeters, Instant.ofEpochMilli(gps.capturedAtEpochMs).toString(), photo, ocr, decision)
                    currentSession = sessionState.open(sessionId, driverId, vehicleId, odo)
                    refreshPending(); SyncScheduler.enqueue(context, BuildConfig.API_BASE_URL)
                    status = if (decision == OdometerVerifier.Decision.PASS) "Session saved • OCR PASS • sync queued" else "Session saved • OCR REVIEW • sync queued"
                    screen = "HOME"
                }, onCancel = { screen = "HOME" }
            )
            screen == "CLOSE" && currentSession != null && identity.driverId != null && identity.vehicleId != null -> SessionCloseScreen(
                sessionId = currentSession!!.sessionId, driverId = identity.driverId!!, vehicleId = identity.vehicleId!!, startOdometer = currentSession!!.startOdometer,
                onClosed = { sessionState.close(); currentSession = null; status = "Session close saved • sync queued"; refreshPending(); SyncScheduler.enqueue(context, BuildConfig.API_BASE_URL); screen = "HOME" },
                onCancel = { screen = "HOME" }
            )
            screen == "ENTRY" && currentSession != null && identity.driverId != null && identity.vehicleId != null -> TransactionEntryScreen(
                type = entryType, sessionId = currentSession!!.sessionId, driverId = identity.driverId!!, vehicleId = identity.vehicleId!!,
                onSaved = { status = "${entryType.replaceFirstChar { it.uppercase() }} saved • sync queued"; refreshPending(); SyncScheduler.enqueue(context, BuildConfig.API_BASE_URL); screen = "HOME" }, onCancel = { screen = "HOME" }
            )
            else -> {
                Column(
                    Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Column {
                            Text("Cab Operations", style = MaterialTheme.typography.headlineSmall)
                            Text(if (displayName.isBlank()) "Driver" else displayName, style = MaterialTheme.typography.bodyLarge)
                            if (registration.isNotBlank()) Text(registration, style = MaterialTheme.typography.labelLarge)
                        }
                        if (pendingCount == 0 && exhaustedCount == 0) AssistChip(onClick = {}, label = { Text("SYNCED") })
                        else AssistChip(onClick = {}, label = { Text("$pendingCount PENDING") })
                    }

                    if (identity.driverId == null || identity.vehicleId == null) {
                        Card(Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text("No active vehicle assignment", style = MaterialTheme.typography.titleMedium)
                                Text("Ask the admin to assign a vehicle before starting a session.")
                            }
                        }
                    } else {
                        Card(Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                Text("TODAY'S SESSION", style = MaterialTheme.typography.labelLarge)
                                Text(if (currentSession == null) "Not started" else "Session open", style = MaterialTheme.typography.headlineSmall)
                                currentSession?.let { Text("Started at ${it.startOdometer.toInt()} km") }
                                if (currentSession == null) {
                                    Button({ screen = if (driverPermissionsGranted(context)) "START" else "PERMISSIONS" }, Modifier.fillMaxWidth()) { Text("START SESSION") }
                                } else {
                                    Text("Record every trip, fuel fill and expense while the session is open.", style = MaterialTheme.typography.bodyMedium)
                                }
                            }
                        }

                        if (currentSession != null) {
                            Text("RECORD", style = MaterialTheme.typography.labelLarge)
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                Button({ entryType = "TRIP"; screen = "ENTRY" }, Modifier.weight(1f)) { Text("TRIP") }
                                Button({ entryType = "FUEL"; screen = "ENTRY" }, Modifier.weight(1f)) { Text("FUEL") }
                            }
                            OutlinedButton({ entryType = "EXPENSE"; screen = "ENTRY" }, Modifier.fillMaxWidth()) { Text("ADD EXPENSE") }
                            OutlinedButton({ screen = "CLOSE" }, Modifier.fillMaxWidth()) { Text("CLOSE SESSION") }
                        }
                    }

                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                            Text("SYNC STATUS", style = MaterialTheme.typography.labelLarge)
                            Text(if (pendingCount == 0) "All saved data is synced" else "$pendingCount item${if (pendingCount == 1) "" else "s"} waiting to sync")
                            if (exhaustedCount > 0) Text("$exhaustedCount item${if (exhaustedCount == 1) "" else "s"} need attention after repeated failures.", color = MaterialTheme.colorScheme.error)
                            Text("You can continue working offline. Data stays on the phone until the server accepts it.", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                    if (status.isNotBlank()) Text(status, style = MaterialTheme.typography.bodyMedium)
                    TextButton(onClick = { showLogoutConfirm = true }, modifier = Modifier.align(Alignment.CenterHorizontally)) { Text("LOG OUT") }
                    Text("Cab Operations • Offline-first", style = MaterialTheme.typography.labelSmall, modifier = Modifier.align(Alignment.CenterHorizontally))
                }
            }
        }

        if (showLogoutConfirm) {
            AlertDialog(
                onDismissRequest = { showLogoutConfirm = false },
                title = { Text("Log out?") },
                text = { Text(if (pendingCount > 0) "There are $pendingCount unsynced items. Logging out is safe, but keep the app installed so they can sync later." else "You can log in again at any time.") },
                confirmButton = {
                    TextButton(onClick = { showLogoutConfirm = false; auth.logout(); identity.clearAssignment(); sessionState.close(); currentSession = null; screen = "LOGIN" }) { Text("LOG OUT") }
                },
                dismissButton = { TextButton(onClick = { showLogoutConfirm = false }) { Text("CANCEL") } },
            )
        }
    }
}
