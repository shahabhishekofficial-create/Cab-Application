package com.caboperations.driver.ui

import android.content.pm.PackageManager
import android.net.Uri
import android.os.SystemClock
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.work.WorkInfo
import androidx.work.WorkManager
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

private fun permissionsGranted(context: android.content.Context): Boolean = ContextCompat.checkSelfPermission(context, android.Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED && (ContextCompat.checkSelfPermission(context, android.Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED || ContextCompat.checkSelfPermission(context, android.Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED)

@Composable
fun DriverAppClean(oauthUri: Uri? = null, onOAuthUriConsumed: () -> Unit = {}) {
    val context = LocalContext.current; val identity = remember { DriverIdentity(context) }; val auth = remember { AuthRepository(context) }; val driverContext = remember { DriverContextRepository() }; val state = remember { SessionStateRepository(context) }; val local = remember { SessionLocalRepository(context) }; val scope = rememberCoroutineScope()
    var session by remember { mutableStateOf(state.current()) }; var screen by remember { mutableStateOf("LOADING") }; var type by remember { mutableStateOf("TRIP") }; var name by remember { mutableStateOf("") }; var registration by remember { mutableStateOf("") }; var pending by remember { mutableIntStateOf(0) }; var menuOpen by remember { mutableStateOf(false) }; var lastBack by remember { mutableLongStateOf(0L) }
    fun restoreSession() { session = state.current()?.takeIf { it.driverId == identity.driverId && it.vehicleId == identity.vehicleId } }
    suspend fun refreshPending() { pending = runCatching { withContext(Dispatchers.IO) { CabDatabase.get(context).pendingTransactionDao().pendingCount() } }.getOrDefault(0) }
    suspend fun loadDriver(): Boolean { val s = auth.session() ?: return false; val result = withContext(Dispatchers.IO) { driverContext.load(s.accessToken) }; if (result.isSuccess) { val c = result.getOrThrow(); val vehicle = c.vehicleId ?: return false; identity.configure(c.driverId, vehicle); name = c.displayName; registration = c.registrationNumber.orEmpty(); restoreSession(); return true }; if (identity.driverId != null && identity.vehicleId != null) { restoreSession(); return true }; return false }
    LaunchedEffect(Unit) { refreshPending(); if (auth.session() != null) SyncScheduler.enqueue(context, BuildConfig.API_BASE_URL); screen = if (loadDriver()) { if (permissionsGranted(context)) "HOME" else "PERMISSIONS" } else "LOGIN" }
    LaunchedEffect(oauthUri) { val uri = oauthUri ?: return@LaunchedEffect; onOAuthUriConsumed(); if (uri.scheme == "cabdriver" && uri.host == "auth-callback") { val result = withContext(Dispatchers.IO) { auth.consumeGoogleCallback(uri) }; screen = if (result.isSuccess && loadDriver()) { if (permissionsGranted(context)) "HOME" else "PERMISSIONS" } else "LOGIN" } }
    BackHandler(enabled = screen in setOf("START", "CLOSE", "ENTRY", "SYNC_STATUS", "SETTINGS", "HELP")) { menuOpen = false; screen = "HOME"; scope.launch { refreshPending() } }
    BackHandler(enabled = screen == "HOME") { val now = SystemClock.elapsedRealtime(); if (now - lastBack < 2000L) (context as? android.app.Activity)?.finish() else { lastBack = now; Toast.makeText(context, "Press back again to exit", Toast.LENGTH_SHORT).show() } }
    MaterialTheme(colorScheme = darkColorScheme(primary = CabAccent, secondary = CabAccent, background = CabDark, surface = CabSurface, onSurface = CabText, onBackground = CabText, onPrimary = Color.Black)) {
        when (screen) {
            "LOADING" -> Box(Modifier.fillMaxSize().background(CabPageBackground), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = CabAccent) }
            "LOGIN" -> LoginScreen(auth) { scope.launch { if (loadDriver()) screen = if (permissionsGranted(context)) "HOME" else "PERMISSIONS" } }
            "PERMISSIONS" -> PermissionGateScreen { screen = "HOME" }
            "START" -> SessionStartScreen(identity.driverId!!, identity.vehicleId!!, onStart = { odo: Double, gps: LocationSnapshot, photo: String, ocr: OdometerOcrResult, decision: OdometerVerifier.Decision -> scope.launch { val id = local.queueStartSession(identity.driverId!!, identity.vehicleId!!, identity.deviceId, odo, gps.latitude, gps.longitude, gps.accuracyMeters, Instant.ofEpochMilli(gps.capturedAtEpochMs).toString(), photo, ocr, decision); session = state.open(id, identity.driverId!!, identity.vehicleId!!, odo); SyncScheduler.enqueue(context, BuildConfig.API_BASE_URL); refreshPending(); screen = "HOME" } }, onCancel = { screen = "HOME" })
            "CLOSE" -> session?.let { s -> SessionCloseScreenClean(s.sessionId, identity.driverId!!, identity.vehicleId!!, s.startOdometer, onClosed = { state.close(); session = null; SyncScheduler.enqueue(context, BuildConfig.API_BASE_URL); scope.launch { refreshPending() }; screen = "HOME" }, onCancel = { screen = "HOME" }) }
            "ENTRY" -> session?.let { s -> TransactionEntryScreenClean(type, s.sessionId, identity.driverId!!, identity.vehicleId!!, onSaved = { SyncScheduler.enqueue(context, BuildConfig.API_BASE_URL); scope.launch { refreshPending() }; screen = "HOME" }, onCancel = { screen = "HOME" }) }
            "SYNC_STATUS" -> SyncStatusScreen({ screen = "HOME" }, { SyncScheduler.enqueue(context, BuildConfig.API_BASE_URL); scope.launch { refreshPending() } })
            "SETTINGS" -> SimpleInfoScreen("Settings", "Driver preferences and device configuration.", { screen = "HOME" })
            "HELP" -> SimpleInfoScreen("Help & support", "For testing, use Sync status / history to inspect the local queue and WorkManager execution state.", { screen = "HOME" })
            else -> DriverHomeClean(name, registration, session, pending, menuOpen, { menuOpen = it }, { screen = if (permissionsGranted(context)) "START" else "PERMISSIONS" }, { type = "TRIP"; screen = "ENTRY" }, { type = "FUEL"; screen = "ENTRY" }, { type = "EXPENSE"; screen = "ENTRY" }, { screen = "CLOSE" }, { auth.logout(); identity.clearAssignment(); state.close(); session = null; pending = 0; screen = "LOGIN" }, { screen = "SYNC_STATUS" }, { screen = "SETTINGS" }, { screen = "HELP" })
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DriverHomeClean(name: String, registration: String, session: SessionStateRepository.State?, pending: Int, menuOpen: Boolean, onMenuChange: (Boolean) -> Unit, onStart: () -> Unit, onTrip: () -> Unit, onFuel: () -> Unit, onExpense: () -> Unit, onClose: () -> Unit, onLogout: () -> Unit, onSync: () -> Unit, onSettings: () -> Unit, onHelp: () -> Unit) {
    var showAbout by remember { mutableStateOf(false) }
    Scaffold(containerColor = CabDark, topBar = { TopAppBar(title = { Text("Cab Ops", fontWeight = FontWeight.Bold) }, actions = { Box { IconButton(onClick = { onMenuChange(true) }) { Text("⋮", style = MaterialTheme.typography.headlineMedium, color = CabText) }; DropdownMenu(expanded = menuOpen, onDismissRequest = { onMenuChange(false) }) {
        DropdownMenuItem(text = { Text("Settings") }, onClick = { onMenuChange(false); onSettings() })
        DropdownMenuItem(text = { Text("Sync status / history") }, onClick = { onMenuChange(false); onSync() })
        DropdownMenuItem(text = { Text("Help & support") }, onClick = { onMenuChange(false); onHelp() })
        DropdownMenuItem(text = { Text("About / version") }, onClick = { showAbout = true; onMenuChange(false) })
        DropdownMenuItem(text = { Text("Logout") }, onClick = { onMenuChange(false); onLogout() })
    } } }) }) { padding ->
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(padding).padding(horizontal = 18.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Spacer(Modifier.height(4.dp)); if (name.isNotBlank()) Text("Hi, ${name.substringBefore(" ")}", color = CabMuted); if (registration.isNotBlank()) Text(registration, style = MaterialTheme.typography.labelLarge, color = CabMuted)
            if (session == null) CabDarkCard { Text("READY", color = CabAccent, fontWeight = FontWeight.Bold); Text("Start your driving session", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold); Text("Your work stays on the phone immediately and syncs when connected.", color = CabMuted); Spacer(Modifier.height(12.dp)); Button(onClick = onStart, modifier = Modifier.fillMaxWidth().height(58.dp), shape = RoundedCornerShape(18.dp), colors = ButtonDefaults.buttonColors(containerColor = CabAccent, contentColor = Color.Black)) { Text("START SESSION", fontWeight = FontWeight.Bold) }
            } else { CabDarkCard { Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text("SESSION ACTIVE", color = CabAccent, fontWeight = FontWeight.Bold); Text(if (pending == 0) "SYNCED" else "$pending PENDING", color = if (pending == 0) CabAccent else Color(0xFFFFC857), fontWeight = FontWeight.Bold) }; Spacer(Modifier.height(18.dp)); Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) { Stat("${session.startOdometer}", "START KM", Modifier.weight(1f)); Stat("OPEN", "STATUS", Modifier.weight(1f)) } }; Text("Record work", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold); Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) { ActionButton("Trip", onTrip, Modifier.weight(1f)); ActionButton("Fuel", onFuel, Modifier.weight(1f)) }; ActionButton("Add expense", onExpense, Modifier.fillMaxWidth()); Button(onClick = onClose, modifier = Modifier.fillMaxWidth().height(58.dp), shape = RoundedCornerShape(18.dp), colors = ButtonDefaults.buttonColors(containerColor = CabAccent, contentColor = Color.Black)) { Text("END SESSION", fontWeight = FontWeight.Bold) } }
            if (pending > 0) Text("$pending item(s) waiting to sync", color = Color(0xFFFFC857), style = MaterialTheme.typography.labelMedium); Spacer(Modifier.height(20.dp))
        }
    }
    if (showAbout) AlertDialog(onDismissRequest = { showAbout = false }, title = { Text("Cab Ops") }, text = { Text("Driver app ${BuildConfig.VERSION_NAME}\nOffline-first operations with automatic sync.") }, confirmButton = { TextButton(onClick = { showAbout = false }) { Text("OK") } })
}

@Composable
private fun SyncStatusScreen(onBack: () -> Unit, onSync: () -> Unit) {
    val context = LocalContext.current; val scope = rememberCoroutineScope(); var pending by remember { mutableIntStateOf(0) }; var failed by remember { mutableIntStateOf(0) }; var workState by remember { mutableStateOf("CHECKING") }; var updated by remember { mutableLongStateOf(0L) }
    fun refresh() { scope.launch(Dispatchers.IO) { val dao = CabDatabase.get(context).pendingTransactionDao(); val p = dao.pendingCount(); val f = dao.exhaustedCount(); val infos = WorkManager.getInstance(context).getWorkInfosForUniqueWork("cab-offline-sync").get(); val w = infos.firstOrNull()?.state ?: WorkInfo.State.BLOCKED; withContext(Dispatchers.Main) { pending = p; failed = f; workState = w.name; updated = System.currentTimeMillis() } } }
    LaunchedEffect(Unit) { refresh() }
    Column(Modifier.fillMaxSize().background(CabDark).verticalScroll(rememberScrollState()).padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        CabHeader("Sync status / history", "Live local queue and WorkManager state", onBack)
        CabCard { Text("LOCAL QUEUE", color = CabMuted, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold); Text("$pending pending", style = MaterialTheme.typography.headlineSmall, color = CabInk, fontWeight = FontWeight.Bold); if (failed > 0) Text("$failed failed after retry limit", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.SemiBold); Text("Pending records stay on this phone until accepted by the server.", color = CabGray) }
        CabCard { Text("WORKMANAGER", color = CabMuted, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold); Text(workState, style = MaterialTheme.typography.titleLarge, color = if (workState == "RUNNING") CabAccent else CabInk, fontWeight = FontWeight.Bold); Text("Actual unique-work execution state.", color = CabGray) }
        CabPrimaryButton("SYNC NOW") { onSync(); refresh() }; CabSecondaryButton("REFRESH STATUS") { refresh() }
        Text("Checked at ${if (updated == 0L) "—" else java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date(updated))}", color = CabMuted, style = MaterialTheme.typography.labelSmall)
    }
}

@Composable private fun SimpleInfoScreen(title: String, text: String, onBack: () -> Unit) { Column(Modifier.fillMaxSize().background(CabDark).padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) { CabHeader(title, onBack = onBack); CabCard { Text(text, color = CabGray); CabPrimaryButton("BACK") { onBack() } } }
@Composable private fun Stat(value: String, label: String, modifier: Modifier = Modifier) { Column(modifier) { Text(value, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold); Text(label, color = CabMuted, style = MaterialTheme.typography.labelSmall) } }
@Composable private fun ActionButton(label: String, onClick: () -> Unit, modifier: Modifier) { Button(onClick = onClick, modifier = modifier.height(54.dp), shape = RoundedCornerShape(16.dp), colors = ButtonDefaults.buttonColors(containerColor = CabSurface, contentColor = CabText)) { Text(label, fontWeight = FontWeight.Bold) } }
@Composable private fun CabDarkCard(content: @Composable ColumnScope.() -> Unit) { Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(containerColor = CabSurface)) { Column(Modifier.padding(20.dp), content = content) } }
