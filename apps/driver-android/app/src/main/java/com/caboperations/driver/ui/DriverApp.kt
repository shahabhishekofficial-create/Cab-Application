package com.caboperations.driver.ui

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant

private fun driverPermissionsGranted(context: android.content.Context): Boolean =
    ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED &&
        (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED)

private val CabBackground = Color(0xFFF7F8F6)
private val CabPrimary = Color(0xFF5B3FA8)
private val CabPrimarySoft = Color(0xFFECE7FA)
private val CabSuccess = Color(0xFF247A4A)
private val CabSuccessSoft = Color(0xFFE4F4EA)
private val CabWarning = Color(0xFF9A6200)
private val CabWarningSoft = Color(0xFFFFF0D5)
private val CabText = Color(0xFF202124)
private val CabMuted = Color(0xFF686B70)

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

    fun syncNow() {
        status = "Sync started • your data is being sent safely"
        SyncScheduler.enqueue(context, BuildConfig.API_BASE_URL)
        refreshPending()
    }

    fun restoreLocalSessionIfValid() {
        val cached = sessionState.current() ?: return
        if (cached.driverId == identity.driverId && cached.vehicleId == identity.vehicleId) {
            currentSession = cached
        } else {
            sessionState.close()
            currentSession = null
            status = "Old session data was cleared safely"
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
            status = "Offline mode • your local data is safe"
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
            status = "Welcome back • vehicle assignment loaded"
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

    LaunchedEffect(status) {
        if (status.isNotBlank()) {
            delay(5000)
            status = ""
        }
    }

    MaterialTheme(
        colorScheme = lightColorScheme(
            primary = CabPrimary,
            onPrimary = Color.White,
            background = CabBackground,
            surface = Color.White,
            onSurface = CabText,
            onBackground = CabText,
        )
    ) {
        when {
            screen == "LOADING" -> Box(Modifier.fillMaxSize().background(CabBackground), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    CircularProgressIndicator(color = CabPrimary)
                    Text("Getting things ready…", fontWeight = FontWeight.Medium)
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
                    status = if (decision == OdometerVerifier.Decision.PASS) "Session saved • ready to record trips" else "Session saved • odometer marked for review"
                    screen = "HOME"
                }, onCancel = { screen = "HOME" }
            )
            screen == "CLOSE" && currentSession != null && identity.driverId != null && identity.vehicleId != null -> SessionCloseScreen(
                sessionId = currentSession!!.sessionId, driverId = identity.driverId!!, vehicleId = identity.vehicleId!!, startOdometer = currentSession!!.startOdometer,
                onClosed = { sessionState.close(); currentSession = null; status = "Session closed • syncing in background"; refreshPending(); SyncScheduler.enqueue(context, BuildConfig.API_BASE_URL); screen = "HOME" },
                onCancel = { screen = "HOME" }
            )
            screen == "ENTRY" && currentSession != null && identity.driverId != null && identity.vehicleId != null -> TransactionEntryScreen(
                type = entryType, sessionId = currentSession!!.sessionId, driverId = identity.driverId!!, vehicleId = identity.vehicleId!!,
                onSaved = { status = "${entryType.replaceFirstChar { it.uppercase() }} saved • syncing in background"; refreshPending(); SyncScheduler.enqueue(context, BuildConfig.API_BASE_URL); screen = "HOME" }, onCancel = { screen = "HOME" }
            )
            else -> {
                Column(
                    Modifier.fillMaxSize().background(CabBackground).verticalScroll(rememberScrollState()).padding(horizontal = 18.dp, vertical = 20.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            Text("Cab Operations", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                            Text(if (displayName.isBlank()) "Good to see you" else "Hi, ${displayName.substringBefore(" ")}", color = CabMuted)
                        }
                        Surface(shape = RoundedCornerShape(18.dp), color = if (pendingCount == 0 && exhaustedCount == 0) CabSuccessSoft else CabWarningSoft) {
                            Text(
                                if (pendingCount == 0 && exhaustedCount == 0) "✓ Synced" else "$pendingCount pending",
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                color = if (pendingCount == 0 && exhaustedCount == 0) CabSuccess else CabWarning,
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                    }

                    if (registration.isNotBlank()) {
                        Surface(shape = RoundedCornerShape(16.dp), color = Color.White, tonalElevation = 1.dp) {
                            Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text("YOUR VEHICLE", style = MaterialTheme.typography.labelSmall, color = CabMuted, fontWeight = FontWeight.Bold)
                                    Text(registration, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                                }
                                Text("Assigned", color = CabSuccess, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }

                    if (identity.driverId == null || identity.vehicleId == null) {
                        Card(shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
                            Column(Modifier.padding(22.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text("Vehicle not assigned", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                                Text("Ask the admin to assign a vehicle before starting work.", color = CabMuted)
                            }
                        }
                    } else if (currentSession == null) {
                        Card(shape = RoundedCornerShape(26.dp), colors = CardDefaults.cardColors(containerColor = CabPrimary)) {
                            Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                                Text("Ready for today?", color = Color.White, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                                Text("Start your session with an odometer photo and GPS check.", color = Color.White.copy(alpha = 0.88f))
                                Button(
                                    onClick = { screen = if (driverPermissionsGranted(context)) "START" else "PERMISSIONS" },
                                    modifier = Modifier.fillMaxWidth().height(52.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = CabPrimary),
                                    shape = RoundedCornerShape(16.dp),
                                ) { Text("Start Session", fontWeight = FontWeight.Bold) }
                            }
                        }
                    } else {
                        Card(shape = RoundedCornerShape(26.dp), colors = CardDefaults.cardColors(containerColor = CabPrimarySoft)) {
                            Column(Modifier.padding(22.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                    Text("SESSION IN PROGRESS", style = MaterialTheme.typography.labelSmall, color = CabPrimary, fontWeight = FontWeight.Bold)
                                    Surface(shape = RoundedCornerShape(20.dp), color = CabSuccessSoft) { Text("● Active", modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp), color = CabSuccess, fontWeight = FontWeight.Bold) }
                                }
                                Text("Started at ${currentSession!!.startOdometer.toInt()} km", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                                Text("Record trips, fuel and expenses as you work.", color = CabMuted)
                            }
                        }

                        Text("QUICK ACTIONS", style = MaterialTheme.typography.labelSmall, color = CabMuted, fontWeight = FontWeight.Bold)
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            ActionCard("Trip", "Record fare", CabPrimarySoft, Modifier.weight(1f)) { entryType = "TRIP"; screen = "ENTRY" }
                            ActionCard("Fuel", "Add filling", CabWarningSoft, Modifier.weight(1f)) { entryType = "FUEL"; screen = "ENTRY" }
                            ActionCard("Expense", "Add cost", Color(0xFFE8EEF8), Modifier.weight(1f)) { entryType = "EXPENSE"; screen = "ENTRY" }
                        }
                        OutlinedButton(onClick = { screen = "CLOSE" }, modifier = Modifier.fillMaxWidth().height(50.dp), shape = RoundedCornerShape(15.dp)) {
                            Text("Close Session", fontWeight = FontWeight.SemiBold)
                        }
                    }

                    Card(shape = RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
                        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text("SYNC", style = MaterialTheme.typography.labelSmall, color = CabMuted, fontWeight = FontWeight.Bold)
                                    Text(if (pendingCount == 0) "Everything is up to date" else "$pendingCount item${if (pendingCount == 1) "" else "s"} waiting to upload", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                }
                                if (pendingCount > 0) {
                                    Button(onClick = { syncNow() }, shape = RoundedCornerShape(14.dp)) { Text("Sync Now") }
                                }
                            }
                            if (exhaustedCount > 0) {
                                Text("$exhaustedCount item${if (exhaustedCount == 1) "" else "s"} need attention after repeated failures.", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                            } else {
                                Text("You can keep working without internet. Saved entries stay on this phone until accepted by the server.", color = CabMuted, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }

                    if (status.isNotBlank()) {
                        Surface(shape = RoundedCornerShape(14.dp), color = CabSuccessSoft) {
                            Text(status, modifier = Modifier.fillMaxWidth().padding(12.dp), color = CabSuccess, fontWeight = FontWeight.Medium)
                        }
                    }

                    TextButton(onClick = { showLogoutConfirm = true }, modifier = Modifier.align(Alignment.CenterHorizontally)) { Text("Log out") }
                    Spacer(Modifier.height(8.dp))
                    Text("Cab Operations • Works offline", modifier = Modifier.align(Alignment.CenterHorizontally), color = CabMuted, style = MaterialTheme.typography.labelSmall)
                }
            }
        }

        if (showLogoutConfirm) {
            AlertDialog(
                onDismissRequest = { showLogoutConfirm = false },
                title = { Text("Log out?") },
                text = { Text(if (pendingCount > 0) "You still have $pendingCount unsynced item${if (pendingCount == 1) "" else "s"}. They remain safely on this phone." else "You can log in again at any time.") },
                confirmButton = { TextButton(onClick = { showLogoutConfirm = false; auth.logout(); identity.clearAssignment(); sessionState.close(); currentSession = null; screen = "LOGIN" }) { Text("Log out") } },
                dismissButton = { TextButton(onClick = { showLogoutConfirm = false }) { Text("Cancel") } },
            )
        }
    }
}

@Composable
private fun ActionCard(title: String, subtitle: String, background: Color, modifier: Modifier, onClick: () -> Unit) {
    Surface(onClick = onClick, modifier = modifier.clip(RoundedCornerShape(20.dp)), color = background) {
        Column(Modifier.padding(14.dp).height(82.dp), verticalArrangement = Arrangement.SpaceBetween) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = CabMuted)
        }
    }
}
