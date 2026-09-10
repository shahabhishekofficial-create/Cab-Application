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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun DriverApp() {
    var sessionOpen by remember { mutableStateOf(false) }

    MaterialTheme {
        Column(
            modifier = Modifier.fillMaxSize().padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text("Cab Driver", style = MaterialTheme.typography.headlineMedium)
            Text("Driver • Vehicle", style = MaterialTheme.typography.bodyLarge)

            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("SESSION", style = MaterialTheme.typography.labelLarge)
                    Text(if (sessionOpen) "OPEN" else "NOT STARTED", style = MaterialTheme.typography.titleLarge)
                    Text("Pending sync: 0")
                }
            }

            if (!sessionOpen) {
                Button(onClick = { sessionOpen = true }, modifier = Modifier.fillMaxWidth()) {
                    Text("START SESSION")
                }
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    Button(onClick = {}, modifier = Modifier.weight(1f)) { Text("ADD TRIP") }
                    Button(onClick = {}, modifier = Modifier.weight(1f)) { Text("FUEL") }
                }
                OutlinedButton(onClick = {}, modifier = Modifier.fillMaxWidth()) { Text("EXPENSE") }
                OutlinedButton(onClick = { sessionOpen = false }, modifier = Modifier.fillMaxWidth()) { Text("CLOSE SESSION") }
            }

            Text("Offline-first: entries are saved locally and synced when connectivity returns.")
        }
    }
}
