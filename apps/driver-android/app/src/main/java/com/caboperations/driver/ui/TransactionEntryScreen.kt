package com.caboperations.driver.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.caboperations.driver.data.ExpenseLocalRepository
import com.caboperations.driver.data.FuelLocalRepository
import com.caboperations.driver.data.TripLocalRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

private enum class EntryType { TRIP, FUEL, EXPENSE }

@Composable
fun TransactionEntryScreen(
    type: String,
    sessionId: String,
    driverId: String,
    vehicleId: String,
    onSaved: (String) -> Unit,
    onCancel: () -> Unit
) {
    val entryType = runCatching { EntryType.valueOf(type) }.getOrElse { EntryType.TRIP }
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = androidx.compose.runtime.rememberCoroutineScope()

    var startOdo by remember { mutableStateOf("") }
    var endOdo by remember { mutableStateOf("") }
    var fare by remember { mutableStateOf("") }
    var platform by remember { mutableStateOf("Uber") }
    var payment by remember { mutableStateOf("UPI") }
    var status by remember { mutableStateOf("COMPLETED") }
    var pickup by remember { mutableStateOf("") }
    var dropoff by remember { mutableStateOf("") }
    var fuelType by remember { mutableStateOf("CNG") }
    var quantity by remember { mutableStateOf("") }
    var unit by remember { mutableStateOf("KG") }
    var rate by remember { mutableStateOf("") }
    var amount by remember { mutableStateOf("") }
    var category by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }

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
                        TripLocalRepository(context).queueTrip(
                            sessionId, driverId, vehicleId, start, end, gross, status,
                            pickup = pickup.ifBlank { null }, dropoff = dropoff.ifBlank { null },
                            paymentMethod = payment.ifBlank { null }, notes = notes.ifBlank { null }
                        )
                    }
                    EntryType.FUEL -> {
                        val odo = startOdo.toDoubleOrNull() ?: error("Enter odometer")
                        val qty = quantity.toDoubleOrNull() ?: error("Enter quantity")
                        val fuelRate = rate.toDoubleOrNull() ?: error("Enter rate")
                        val total = amount.toDoubleOrNull() ?: error("Enter amount")
                        FuelLocalRepository(context).queueFuel(
                            sessionId, driverId, vehicleId, fuelType, odo, qty, unit, fuelRate, total,
                            paymentMethod = payment.ifBlank { null }, notes = notes.ifBlank { null }
                        )
                    }
                    EntryType.EXPENSE -> {
                        val total = amount.toDoubleOrNull() ?: error("Enter amount")
                        ExpenseLocalRepository(context).queueExpense(
                            sessionId, driverId, vehicleId, total,
                            categoryId = category.ifBlank { null },
                            paymentMethod = payment.ifBlank { null },
                            odometer = startOdo.toDoubleOrNull(), notes = notes.ifBlank { null }
                        )
                    }
                }
                onSaved(id)
            } catch (e: IllegalArgumentException) {
                error = e.message ?: "Invalid entry"
            } finally {
                busy = false
            }
        }
    }

    Column(
        modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(entryType.name.replace('_', ' '))
        Text("Session: $sessionId")

        when (entryType) {
            EntryType.TRIP -> {
                OutlinedTextField(startOdo, { startOdo = it }, label = { Text("Start odometer") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(endOdo, { endOdo = it }, label = { Text("End odometer") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(fare, { fare = it }, label = { Text("Gross fare ₹") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(platform, { platform = it }, label = { Text("Platform") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(payment, { payment = it }, label = { Text("Payment method") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(pickup, { pickup = it }, label = { Text("Pickup") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(dropoff, { dropoff = it }, label = { Text("Drop") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(status, { status = it }, label = { Text("Status") }, modifier = Modifier.fillMaxWidth())
            }
            EntryType.FUEL -> {
                OutlinedTextField(startOdo, { startOdo = it }, label = { Text("Odometer") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(fuelType, { fuelType = it }, label = { Text("Fuel type") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(quantity, { quantity = it }, label = { Text("Quantity") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(unit, { unit = it }, label = { Text("Unit") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(rate, { rate = it }, label = { Text("Rate ₹") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(amount, { amount = it }, label = { Text("Amount ₹") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(payment, { payment = it }, label = { Text("Payment method") }, modifier = Modifier.fillMaxWidth())
            }
            EntryType.EXPENSE -> {
                OutlinedTextField(amount, { amount = it }, label = { Text("Amount ₹") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(category, { category = it }, label = { Text("Category ID (optional)") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(payment, { payment = it }, label = { Text("Payment method") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(startOdo, { startOdo = it }, label = { Text("Odometer (optional)") }, modifier = Modifier.fillMaxWidth())
            }
        }

        OutlinedTextField(notes, { notes = it }, label = { Text("Notes") }, modifier = Modifier.fillMaxWidth())
        if (error.isNotBlank()) Text(error)
        Button(enabled = !busy, onClick = { save() }, modifier = Modifier.fillMaxWidth()) { Text(if (busy) "SAVING…" else "SAVE OFFLINE") }
        OutlinedButton(enabled = !busy, onClick = onCancel, modifier = Modifier.fillMaxWidth()) { Text("CANCEL") }
    }
}

private fun error(message: String): Nothing = throw IllegalArgumentException(message)
