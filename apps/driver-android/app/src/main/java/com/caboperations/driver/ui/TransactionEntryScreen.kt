package com.caboperations.driver.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.caboperations.driver.BuildConfig
import com.caboperations.driver.auth.AuthRepository
import com.caboperations.driver.data.ExpenseLocalRepository
import com.caboperations.driver.data.FuelLocalRepository
import com.caboperations.driver.data.TripLocalRepository
import com.caboperations.driver.network.ExpenseCategoryOption
import com.caboperations.driver.network.ExpenseCategoryRepository
import com.caboperations.driver.network.PlatformOption
import com.caboperations.driver.network.PlatformRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private enum class EntryType { TRIP, FUEL, EXPENSE }
private val paymentMethods = listOf("CASH", "UPI", "CARD", "BANK", "OTHER")
private val tripStatuses = listOf("COMPLETED", "CANCELLED_BY_CUSTOMER", "CANCELLED_BY_DRIVER", "CUSTOMER_NO_SHOW")

@Composable
fun TransactionEntryScreen(type: String, sessionId: String, driverId: String, vehicleId: String, onSaved: (String) -> Unit, onCancel: () -> Unit) {
    val entryType = runCatching { EntryType.valueOf(type) }.getOrElse { EntryType.TRIP }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val auth = remember { AuthRepository(context) }
    var startOdo by remember { mutableStateOf("") }; var endOdo by remember { mutableStateOf("") }; var fare by remember { mutableStateOf("") }; var additionalCharges by remember { mutableStateOf("0") }
    var payment by remember { mutableStateOf("UPI") }; var status by remember { mutableStateOf("COMPLETED") }; var pickup by remember { mutableStateOf("") }; var dropoff by remember { mutableStateOf("") }
    var fuelType by remember { mutableStateOf("CNG") }; var quantity by remember { mutableStateOf("") }; var unit by remember { mutableStateOf("KG") }; var rate by remember { mutableStateOf("") }; var amount by remember { mutableStateOf("") }
    var category by remember { mutableStateOf<ExpenseCategoryOption?>(null) }; var categories by remember { mutableStateOf<List<ExpenseCategoryOption>>(emptyList()) }; var notes by remember { mutableStateOf("") }
    var platforms by remember { mutableStateOf<List<PlatformOption>>(emptyList()) }; var selectedPlatform by remember { mutableStateOf<PlatformOption?>(null) }
    var platformMenu by remember { mutableStateOf(false) }; var categoryMenu by remember { mutableStateOf(false) }; var paymentMenu by remember { mutableStateOf(false) }; var statusMenu by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }; var error by remember { mutableStateOf("") }

    LaunchedEffect(entryType) {
        error = ""
        val token = withContext(Dispatchers.IO) { auth.refreshIfNeeded() }.getOrNull()?.accessToken
        if (token == null) { error = "Your login session has expired. Please log in again."; return@LaunchedEffect }
        if (entryType == EntryType.TRIP) {
            val result = withContext(Dispatchers.IO) { PlatformRepository(BuildConfig.API_BASE_URL, token).load() }
            if (result.isSuccess) { platforms = result.getOrNull().orEmpty(); selectedPlatform = platforms.firstOrNull() } else error = "Platforms aren’t available right now. You can still save this trip."
        }
        if (entryType == EntryType.EXPENSE) {
            val result = withContext(Dispatchers.IO) { ExpenseCategoryRepository(BuildConfig.API_BASE_URL, token).load() }
            if (result.isSuccess) categories = result.getOrNull().orEmpty() else error = "Categories aren’t available right now. You can save this as Uncategorized."
        }
    }
    val quantityValue = quantity.toDoubleOrNull(); val rateValue = rate.toDoubleOrNull()
    val calculatedFuelAmount = if (quantityValue != null && rateValue != null && quantityValue > 0 && rateValue >= 0) quantityValue * rateValue else null

    fun save() {
        if (busy) return; busy = true; error = ""
        scope.launch {
            try {
                val id = when (entryType) {
                    EntryType.TRIP -> {
                        val start = startOdo.toDoubleOrNull() ?: error("Enter starting odometer")
                        val end = endOdo.toDoubleOrNull() ?: error("Enter ending odometer")
                        val gross = fare.toDoubleOrNull() ?: error("Enter fare")
                        val charges = additionalCharges.toDoubleOrNull() ?: error("Enter additional charges")
                        require(start >= 0 && end >= 0) { "Odometer cannot be negative" }; require(end >= start) { "Ending odometer cannot be less than starting odometer" }; require(gross >= 0) { "Fare cannot be negative" }; require(charges >= 0) { "Additional charges cannot be negative" }
                        TripLocalRepository(context).queueTrip(sessionId, driverId, vehicleId, start, end, gross, status, selectedPlatform?.id, pickup.trim().ifBlank { null }, dropoff.trim().ifBlank { null }, payment, charges, notes.trim().ifBlank { null })
                    }
                    EntryType.FUEL -> {
                        val odo = startOdo.toDoubleOrNull() ?: error("Enter odometer"); val qty = quantityValue ?: error("Enter quantity"); val fuelRate = rateValue ?: error("Enter rate"); val total = calculatedFuelAmount ?: error("Enter valid quantity and rate")
                        require(odo >= 0) { "Odometer cannot be negative" }; require(qty > 0) { "Quantity must be greater than zero" }; require(fuelRate >= 0) { "Rate cannot be negative" }; require(total > 0) { "Amount must be greater than zero" }
                        FuelLocalRepository(context).queueFuel(sessionId, driverId, vehicleId, fuelType.trim(), odo, qty, unit.trim(), fuelRate, total, payment, notes.trim().ifBlank { null })
                    }
                    EntryType.EXPENSE -> {
                        val total = amount.toDoubleOrNull() ?: error("Enter amount"); val odo = startOdo.toDoubleOrNull(); require(total > 0) { "Amount must be greater than zero" }; require(odo == null || odo >= 0) { "Odometer cannot be negative" }
                        ExpenseLocalRepository(context).queueExpense(sessionId, driverId, vehicleId, total, category?.id, payment, odo, notes.trim().ifBlank { null })
                    }
                }
                onSaved(id)
            } catch (e: IllegalArgumentException) { error = e.message ?: "Please check the details" } catch (e: Exception) { error = e.message ?: "Unable to save. Please try again." } finally { busy = false }
        }
    }

    val title = when (entryType) { EntryType.TRIP -> "Record a trip"; EntryType.FUEL -> "Record fuel"; EntryType.EXPENSE -> "Record an expense" }
    val subtitle = when (entryType) { EntryType.TRIP -> "Add the fare and trip details"; EntryType.FUEL -> "Capture today’s fuel filling"; EntryType.EXPENSE -> "Keep every cab expense accounted for" }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        CabHeader(title, subtitle, onBack = onCancel)
        Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Surface(shape = RoundedCornerShape(14.dp), color = CabPurpleSoft) { Text("Session active • entry will be saved on this phone first", modifier = Modifier.fillMaxWidth().padding(12.dp), color = CabPurple, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold) }
            CabCard {
                when (entryType) {
                    EntryType.TRIP -> {
                        CabSectionLabel("TRIP DETAILS")
                        OutlinedTextField(startOdo, { startOdo = it }, label = { Text("Start odometer (km)") }, modifier = Modifier.fillMaxWidth(), singleLine = true, shape = RoundedCornerShape(14.dp))
                        OutlinedTextField(endOdo, { endOdo = it }, label = { Text("End odometer (km)") }, modifier = Modifier.fillMaxWidth(), singleLine = true, shape = RoundedCornerShape(14.dp))
                        OutlinedTextField(fare, { fare = it }, label = { Text("Gross fare (₹)") }, modifier = Modifier.fillMaxWidth(), singleLine = true, shape = RoundedCornerShape(14.dp))
                        OutlinedTextField(additionalCharges, { additionalCharges = it }, label = { Text("Additional charges (₹)") }, modifier = Modifier.fillMaxWidth(), singleLine = true, shape = RoundedCornerShape(14.dp))
                        Box { OutlinedButton({ platformMenu = true }, Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp)) { Text("Platform: ${selectedPlatform?.name ?: "Not selected (optional)"}") }; DropdownMenu(platformMenu, { platformMenu = false }) { platforms.forEach { p -> DropdownMenuItem({ Text(p.name) }, { selectedPlatform = p; platformMenu = false }) } } }
                        Box { OutlinedButton({ paymentMenu = true }, Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp)) { Text("Payment: $payment") }; DropdownMenu(paymentMenu, { paymentMenu = false }) { paymentMethods.forEach { p -> DropdownMenuItem({ Text(p) }, { payment = p; paymentMenu = false }) } } }
                        OutlinedTextField(pickup, { pickup = it }, label = { Text("Pickup (optional)") }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp))
                        OutlinedTextField(dropoff, { dropoff = it }, label = { Text("Drop (optional)") }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp))
                        Box { OutlinedButton({ statusMenu = true }, Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp)) { Text("Trip status: ${status.replace('_', ' ')}") }; DropdownMenu(statusMenu, { statusMenu = false }) { tripStatuses.forEach { s -> DropdownMenuItem({ Text(s.replace('_', ' ')) }, { status = s; statusMenu = false }) } } }
                    }
                    EntryType.FUEL -> {
                        CabSectionLabel("FUEL DETAILS")
                        OutlinedTextField(startOdo, { startOdo = it }, label = { Text("Odometer (km)") }, modifier = Modifier.fillMaxWidth(), singleLine = true, shape = RoundedCornerShape(14.dp))
                        OutlinedTextField(fuelType, { fuelType = it }, label = { Text("Fuel type") }, modifier = Modifier.fillMaxWidth(), singleLine = true, shape = RoundedCornerShape(14.dp))
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) { OutlinedTextField(quantity, { quantity = it }, label = { Text("Quantity") }, modifier = Modifier.weight(1f), singleLine = true, shape = RoundedCornerShape(14.dp)); OutlinedTextField(unit, { unit = it }, label = { Text("Unit") }, modifier = Modifier.weight(1f), singleLine = true, shape = RoundedCornerShape(14.dp)) }
                        OutlinedTextField(rate, { rate = it }, label = { Text("Rate (₹)") }, modifier = Modifier.fillMaxWidth(), singleLine = true, shape = RoundedCornerShape(14.dp))
                        Surface(shape = RoundedCornerShape(14.dp), color = CabAmberSoft) { Text("Total fuel cost  ${calculatedFuelAmount?.let { "₹%.2f".format(it) } ?: "—"}", modifier = Modifier.fillMaxWidth().padding(14.dp), color = CabAmber, fontWeight = FontWeight.Bold) }
                        Box { OutlinedButton({ paymentMenu = true }, Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp)) { Text("Payment: $payment") }; DropdownMenu(paymentMenu, { paymentMenu = false }) { paymentMethods.forEach { p -> DropdownMenuItem({ Text(p) }, { payment = p; paymentMenu = false }) } } }
                    }
                    EntryType.EXPENSE -> {
                        CabSectionLabel("EXPENSE DETAILS")
                        OutlinedTextField(amount, { amount = it }, label = { Text("Amount (₹)") }, modifier = Modifier.fillMaxWidth(), singleLine = true, shape = RoundedCornerShape(14.dp))
                        Box { OutlinedButton({ categoryMenu = true }, Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp)) { Text("Category: ${category?.name ?: "Uncategorized"}") }; DropdownMenu(categoryMenu, { categoryMenu = false }) { DropdownMenuItem({ Text("Uncategorized") }, { category = null; categoryMenu = false }); categories.forEach { c -> DropdownMenuItem({ Text(c.name) }, { category = c; categoryMenu = false }) } } }
                        Box { OutlinedButton({ paymentMenu = true }, Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp)) { Text("Payment: $payment") }; DropdownMenu(paymentMenu, { paymentMenu = false }) { paymentMethods.forEach { p -> DropdownMenuItem({ Text(p) }, { payment = p; paymentMenu = false }) } } }
                        OutlinedTextField(startOdo, { startOdo = it }, label = { Text("Odometer (optional)") }, modifier = Modifier.fillMaxWidth(), singleLine = true, shape = RoundedCornerShape(14.dp))
                    }
                }
                OutlinedTextField(notes, { notes = it }, label = { Text("Notes (optional)") }, modifier = Modifier.fillMaxWidth(), minLines = 2, shape = RoundedCornerShape(14.dp))
            }
            if (error.isNotBlank()) Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.errorContainer) { Text(error, modifier = Modifier.fillMaxWidth().padding(12.dp), color = MaterialTheme.colorScheme.onErrorContainer) }
            CabPrimaryButton(if (busy) "Saving…" else "Save entry", enabled = !busy, onClick = { save() })
            Text("You don’t need internet to save. We’ll sync it automatically when possible.", modifier = Modifier.fillMaxWidth(), color = CabGray, style = MaterialTheme.typography.bodySmall)
            CabSecondaryButton("Cancel", enabled = !busy, onClick = onCancel)
        }
    }
}

private fun error(message: String): Nothing = throw IllegalArgumentException(message)