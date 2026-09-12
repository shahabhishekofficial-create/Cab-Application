package com.caboperations.driver.ui

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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.caboperations.driver.BuildConfig
import com.caboperations.driver.auth.AuthRepository
import com.caboperations.driver.auth.DriverContextRepository
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

private fun permissionsGranted(context: android.content.Context) =
    ContextCompat.checkSelfPermission(context, android.Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED &&
        (ContextCompat.checkSelfPermission(context, android.Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED || ContextCompat.checkSelfPermission(context, android.Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED)

@Composable
fun DriverAppClean(oauthUri: Uri? = null, onOAuthUriConsumed: () -> Unit = {}) {
    val context = LocalContext.current
    val identity = remember { DriverIdentity(context) }
    val auth = remember { AuthRepository(context) }
    val driverContext = remember { DriverContextRepository() }
    val state = remember { SessionStateRepository(context) }
    val local = remember { SessionLocalRepository(context) }
    var session by remember { mutableStateOf(state.current()) }
    var screen by remember { mutableStateOf("LOADING") }
    var type by remember { mutableStateOf("TRIP") }
    var name by remember { mutableStateOf("") }
    var registration by remember { mutableStateOf("") }
    var message by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()

    fun restore() { session = state.current()?.takeIf { it.driverId == identity.driverId && it.vehicleId == identity.vehicleId } }
    suspend fun loadDriver(): Boolean {
        val s = auth.session() ?: return false
        val r = withContext(Dispatchers.IO) { driverContext.load(s.accessToken) }
        if (r.isSuccess) { val c = r.getOrThrow(); val v = c.vehicleId ?: return false; identity.configure(c.driverId, v); name = c.displayName; registration = c.registrationNumber.orEmpty(); restore(); return true }
        if (identity.driverId != null && identity.vehicleId != null) { restore(); return true }
        return false
    }

    LaunchedEffect(Unit) {
        SyncScheduler.enqueue(context, BuildConfig.API_BASE_URL)
        if (loadDriver()) screen = if (permissionsGranted(context)) "HOME" else "PERMISSIONS" else { auth.logout(); identity.clearAssignment(); state.close(); screen = "LOGIN" }
    }
    LaunchedEffect(oauthUri) {
        val uri = oauthUri ?: return@LaunchedEffect
        onOAuthUriConsumed()
        if (uri.scheme != "cabdriver" || uri.host != "auth-callback") return@LaunchedEffect
        val r = withContext(Dispatchers.IO) { auth.consumeGoogleCallback(uri) }
        if (r.isSuccess && loadDriver()) screen = if (permissionsGranted(context)) "HOME" else "PERMISSIONS" else { auth.logout(); identity.clearAssignment(); state.close(); screen = "LOGIN" }
    }

    MaterialTheme(colorScheme = lightColorScheme(primary = CabPurple, background = CabPageBackground, surface = Color.White, onSurface = CabInk, onBackground = CabInk)) {
        when (screen) {
            "LOADING" -> Box(Modifier.fillMaxSize().background(CabPageBackground), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            "LOGIN" -> LoginScreen(auth) { scope.launch { if (loadDriver()) screen = if (permissionsGranted(context)) "HOME" else "PERMISSIONS" } }
            "PERMISSIONS" -> PermissionGateScreen { screen = "HOME" }
            "START" -> SessionStartScreen(identity.driverId!!, identity.vehicleId!!, onStart = { odo: Double, gps: LocationSnapshot, photo: String, ocr: OdometerOcrResult, decision: OdometerVerifier.Decision ->
                val id = local.queueStartSession(identity.driverId!!, identity.vehicleId!!, identity.deviceId, odo, gps.latitude, gps.longitude, gps.accuracyMeters, Instant.ofEpochMilli(gps.capturedAtEpochMs).toString(), photo, ocr, decision)
                session = state.open(id, identity.driverId!!, identity.vehicleId!!, odo); SyncScheduler.enqueue(context, BuildConfig.API_BASE_URL); screen = "HOME"
            }, onCancel = { screen = "HOME" })
            "CLOSE" -> session?.let { s -> SessionCloseScreenClean(s.sessionId, identity.driverId!!, identity.vehicleId!!, s.startOdometer, onClosed = { state.close(); session = null; SyncScheduler.enqueue(context, BuildConfig.API_BASE_URL); screen = "HOME" }, onCancel = { screen = "HOME" }) }
            "ENTRY" -> session?.let { s -> TransactionEntryScreenClean(type, s.sessionId, identity.driverId!!, identity.vehicleId!!, onSaved = { SyncScheduler.enqueue(context, BuildConfig.API_BASE_URL); screen = "HOME" }, onCancel = { screen = "HOME" }) }
            else -> DriverHomeClean(name, registration, session != null,
                onStart = { screen = if (permissionsGranted(context)) "START" else "PERMISSIONS" },
                onTrip = { type = "TRIP"; screen = "ENTRY" }, onFuel = { type = "FUEL"; screen = "ENTRY" }, onExpense = { type = "EXPENSE"; screen = "ENTRY" }, onClose = { screen = "CLOSE" }, onLogout = { auth.logout(); identity.clearAssignment(); state.close(); session = null; screen = "LOGIN" }, message = message)
        }
    }
}

@Composable
private fun DriverHomeClean(name: String, registration: String, active: Boolean, onStart: () -> Unit, onTrip: () -> Unit, onFuel: () -> Unit, onExpense: () -> Unit, onClose: () -> Unit, onLogout: () -> Unit, message: String) {
    Column(Modifier.fillMaxSize().background(CabPageBackground).verticalScroll(rememberScrollState()).padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text("Cab Operations", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        if (name.isNotBlank()) Text("Hi, ${name.substringBefore(" ")}", color = CabGray)
        if (registration.isNotBlank()) CabCard { CabSectionLabel("YOUR VEHICLE"); Text(registration, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold) }
        if (!active) {
            CabCard(color = CabPurpleSoft) { Text("Ready for today?", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold); Text("Start your session before recording work.", color = CabGray); CabPrimaryButton("Start Session", onClick = onStart) }
        } else {
            CabCard(color = CabPurpleSoft) { Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text("SESSION ACTIVE", color = CabPurple, fontWeight = FontWeight.Bold); Text("● Active", color = CabGreen, fontWeight = FontWeight.Bold) }; Text("Record today’s work", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold) }
            CabSectionLabel("RECORD")
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                CabPrimaryButton("Trip", Modifier.weight(1f), onTrip); CabPrimaryButton("Fuel", Modifier.weight(1f), onFuel)
            }
            CabSecondaryButton("Add Expense", onClick = onExpense)
            CabSecondaryButton("Close Session", onClick = onClose)
        }
        if (message.isNotBlank()) Text(message, color = CabGray)
        TextButton(onClick = onLogout, modifier = Modifier.align(Alignment.CenterHorizontally)) { Text("Log out") }
    }
}
