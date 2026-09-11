package com.caboperations.driver.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import kotlin.math.abs

private enum class EntryType { TRIP, FUEL, EXPENSE }
private val paymentMethods = listOf("CASH", "UPI", "CARD", "BANK", "OTHER")
private val tripStatuses = listOf("COMPLETED", "CANCELLED_BY_CUSTOMER", "CANCELLED_BY_DRIVER", "CUSTOMER_NO_SHOW")

@Composable
fun TransactionEntryScreen(type: String, sessionId: String, driverId: String, vehicleId: String, onSaved: (String) -> Unit, onCancel: () -> Unit) {
    val entryType = runCatching { EntryType.valueOf(type) }.getOrElse { EntryType.TRIP }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val auth = remember { AuthRepository(context) }
    var startOdo by remember { mutableStateOf("") }
    var endOdo by remember { mutableStateOf("") }
    var fare by remember { mutableStateOf("") }
    var additionalCharges by remember { mutableStateOf("0") }
    var payment by remember { mutableStateOf("UPI") }
    var status by remember { mutableStateOf("COMPLETED") }
    var pickup by remember { mutableStateOf("") }
    var dropoff by remember { mutableStateOf("") }
    var fuelType by remember { mutableStateOf("CNG") }
    var quantity by remember { mutableStateOf("") }
    var unit by remember { mutableStateOf("KG") }
    var rate by remember { mutableStateOf("") }
    var amount by remember { mutableStateOf("") }
    var category by remember { mutableStateOf<ExpenseCategoryOption?>(null) }
    var categories by remember { mutableStateOf<List<ExpenseCategoryOption>>(emptyList()) }
    var notes by remember { mutableStateOf("") }
    var platforms by remember { mutableStateOf<List<PlatformOption>>(emptyList()) }
    var selectedPlatform by remember { mutableStateOf<PlatformOption?>(null) }
    var platformMenu by remember { mutableStateOf(false) }
    var categoryMenu by remember { mutableStateOf(false) }
    var paymentMenu by remember { mutableStateOf(false) }
    var statusMenu by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }

    LaunchedEffect(entryType) {
        error = ""
        val token = auth.refreshIfNeeded().getOrNull()?.accessToken
        if (token == null) {
            error = "Login session expired. Please log in again."
            return@LaunchedEffect
        }
        if (entryType == EntryType.TRIP) {
            val loaded = withContext(Dispatchers.IO) { PlatformRepository(BuildConfig.API_BASE_URL, token).load().getOrNull().orEmpty() }
            platforms = loaded
            selectedPlatform = loaded.firstOrNull()
        }
        if (entryType == EntryType.EXPENSE) {
            categories = withContext(Dispatchers.IO) { ExpenseCategoryRepository(BuildConfig.API_BASE_URL, token).load().getOrNull().orEmpty() }
        }
    }

    val quantityValue = quantity.toDoubleOrNull()
    val rateValue = rate.toDoubleOrNull()
    val calculatedFuelAmount = if (quantityValue != null && rateValue != null && quantityValue > 0 && rateValue >= 0) quantityValue * rateValue else null

    fun save() {
        if (busy) return
        busy = true
        error = ""
        scope.launch {
            try {
                val id = when (entryType) {
                    EntryType.TRIP -> {
                        val start = startOdo.toDoubleOrNull() ?: error("Enter starting odometer")
                        val end = endOdo.toDoubleOrNull() ?: error("Enter ending odometer")
                        val gross = fare.toDoubleOrNull() ?: error("Enter fare")
                        val charges = additionalCharges.toDoubleOrNull() ?: error("Enter additional charges")
                        require(start >= 0 && end >= 0) { "Odometer cannot be negative" }
                        require(end >= start) { "Ending odometer cannot be less than starting odometer" }
                        require(gross >= 0) { "Fare cannot be negative" }
                        require(charges >= 0) { "Additional charges cannot be negative" }
                        require(status != "COMPLETED" || selectedPlatform != null) { "Select the cab platform" }
                        TripLocalRepository(context).queueTrip(sessionId, driverId, vehicleId, start, end, gross, status, platformId = selectedPlatform?.id, pickup = pickup.trim().ifBlank { null }, dropoff = dropoff.trim().ifBlank { null }, paymentMethod = payment, additionalCharges = charges, notes = notes.trim().ifBlank { null })
                    }
                    EntryType.FUEL -> {
                        val odo = startOdo.toDoubleOrNull() ?: error("Enter odometer")
                        val qty = quantityValue ?: error("Enter quantity")
                        val fuelRate = rateValue ?: error("Enter rate")
                        val total = calculatedFuelAmount ?: error("Enter valid quantity and rate")
                        require(odo >= 0) { "Odometer cannot be negative" }
                        require(qty > 0) { "Quantity must be greater than zero" }
                        require(fuelRate >= 0) { "Rate cannot be negative" }
                        require(total > 0) { "Amount must be greater than zero" }
                        FuelLocalRepository(context).queueFuel(sessionId, driverId, vehicleId, fuelType.trim(), odo, qty, unit.trim(), fuelRate, total, paymentMethod = payment, notes = notes.trim().ifBlank { null })
                    }
                    EntryType.EXPENSE -> {
                        val total = amount.toDoubleOrNull() ?: error("Enter amount")
                        val odo = startOdo.toDoubleOrNull()
                        require(total > 0) { "Amount must be greater than zero" }
                        require(odo == null || odo >= 0) { "Odometer cannot be negative" }
                        ExpenseLocalRepository(context).queueExpense(sessionId, driverId, vehicleId, total, categoryId = category?.id, paymentMethod = payment, odometer = odo, notes = notes.trim().ifBlank { null })
                    }
                }
                onSaved(id)
            } catch (e: IllegalArgumentException) { error = e.message ?: "Invalid entry" }
            catch (e: Exception) { error = e.message ?: "Unable to save entry" }
            finally { busy = false }
        }
    }

    Column(Modifier.fillMaxWidth().padding(16.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(entryType.name.replace('_', ' '))
        Text("Session: $sessionId")
        when (entryType) {
            EntryType.TRIP -> {
                OutlinedTextField(startOdo, { startOdo = it }, label = { Text("Start odometer (km)") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(endOdo, { endOdo = it }, label = { Text("End odometer (km)") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(fare, { fare = it }, label = { Text("Gross fare ₹") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(additionalCharges, { additionalCharges = it }, label = { Text("Additional charges ₹") }, modifier = Modifier.fillMaxWidth())
                Box { OutlinedButton({ platformMenu = true }, Modifier.fillMaxWidth()) { Text("Platform: ${selectedPlatform?.name ?: "Select platform"}") }; DropdownMenu(platformMenu, { platformMenu = false }) { platforms.forEach { p -> DropdownMenuItem(text = { Text(p.name) }, onClick = { selectedPlatform = p; platformMenu = false }) } } }
                Box { OutlinedButton({ paymentMenu = true }, Modifier.fillMaxWidth()) { Text("Payment: $payment") }; DropdownMenu(paymentMenu, { paymentMenu = false }) { paymentMethods.forEach { p -> DropdownMenuItem(text = { Text(p) }, onClick = { payment = p; paymentMenu = false }) } } }
                OutlinedTextField(pickup, { pickup = it }, label = { Text("Pickup (optional)") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(dropoff, { dropoff = it }, label = { Text("Drop (optional)") }, modifier = Modifier.fillMaxWidth())
                Box { OutlinedButton({ statusMenu = true }, Modifier.fillMaxWidth()) { Text("Status: $status") }; DropdownMenu(statusMenu, { statusMenu = false }) { tripStatuses.forEach { s -> DropdownMenuItem(text = { Text(s) }, onClick = { status = s; statusMenu = false }) } } }
            }
            EntryType.FUEL -> {
                OutlinedTextField(startOdo, { startOdo = it }, label = { Text("Odometer (km)") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(fuelType, { fuelType = it }, label = { Text("Fuel type") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(quantity, { quantity = it }, label = { Text("Quantity") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(unit, { unit = it }, label = { Text("Unit") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(rate, { rate = it }, label = { Text("Rate ₹") }, modifier = Modifier.fillMaxWidth())
                Text("Calculated amount: ${calculatedFuelAmount?.let { "₹%.2f".format(it) } ?: "—"}")
                OutlinedTextField(value = amount.ifBlank { calculatedFuelAmount?.let { "%.2f".format(it) } ?: "" }, onValueChange = { amount = it }, label = { Text("Amount ₹ (calculated; editable for review)") }, modifier = Modifier.fillMaxWidth())
                Box { OutlinedButton({ paymentMenu = true }, Modifier.fillMaxWidth()) { Text("Payment: $payment") }; DropdownMenu(paymentMenu, { paymentMenu = false }) { paymentMethods.forEach { p -> DropdownMenuItem(text = { Text(p) }, onClick = { payment = p; paymentMenu = false }) } } }
            }
            EntryType.EXPENSE -> {
                OutlinedTextField(amount, { amount = it }, label = { Text("Amount ₹") }, modifier = Modifier.fillMaxWidth())
                Box { OutlinedButton({ categoryMenu = true }, Modifier.fillMaxWidth()) { Text("Category: ${category?.name ?: "Uncategorized"}") }; DropdownMenu(categoryMenu, { categoryMenu = false }) { DropdownMenuItem(text = { Text("Uncategorized") }, onClick = { category = null; categoryMenu = false }); categories.forEach { c -> DropdownMenuItem(text = { Text(c.name) }, onClick = { category = c; categoryMenu = false }) } } }
                Box { OutlinedButton({ paymentMenu = true }, Modifier.fillMaxWidth()) { Text("Payment: $payment") }; DropdownMenu(paymentMenu, { paymentMenu = false }) { paymentMethods.forEach { p -> DropdownMenuItem(text = { Text(p) }, onClick = { payment = p; paymentMenu = false }) } } }
                OutlinedTextField(startOdo, { startOdo = it }, label = { Text("Odometer (optional)") }, modifier = Modifier.fillMaxWidth())
            }
        }
        OutlinedTextField(notes, { notes = it }, label = { Text("Notes (optional)") }, modifier = Modifier.fillMaxWidth())
        if (error.isNotBlank()) Text(error)
        Button(enabled = !busy, onClick = { save() }, modifier = Modifier.fillMaxWidth()) { Text(if (busy) "SAVING…" else "SAVE OFFLINE") }
        OutlinedButton(enabled = !busy, onClick = onCancel, modifier = Modifier.fillMaxWidth()) { Text("CANCEL") }
    }
}

private fun error(message: String): Nothing = throw IllegalArgumentException(message)
