package com.caboperations.driver.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.caboperations.driver.auth.AuthRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun LoginScreen(auth: AuthRepository, onLoggedIn: () -> Unit) {
    val context = LocalContext.current
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()

    Box(Modifier.fillMaxSize().imePadding()) {
        Column(
            modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Spacer(Modifier.height(28.dp))
            Text("Cab Operations", style = MaterialTheme.typography.headlineMedium)
            Text("Driver sign in", style = MaterialTheme.typography.titleLarge)
            Text("Record trips, fuel and expenses even when you are offline.", style = MaterialTheme.typography.bodyMedium)

            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedButton(
                        onClick = {
                            error = ""
                            auth.googleAuthorizeUrl().onSuccess { url ->
                                runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply { addCategory(Intent.CATEGORY_BROWSABLE) }) }
                                    .onFailure { error = it.message ?: "Unable to open Google sign in" }
                            }.onFailure { error = it.message ?: "Google sign in unavailable" }
                        },
                        enabled = !busy,
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("CONTINUE WITH GOOGLE") }

                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        HorizontalDivider(Modifier.weight(1f)); Text("  OR  ", style = MaterialTheme.typography.labelSmall); HorizontalDivider(Modifier.weight(1f))
                    }
                    Text("Email and password", style = MaterialTheme.typography.titleSmall)
                    OutlinedTextField(
                        value = email,
                        onValueChange = { email = it; error = "" },
                        label = { Text("Email") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        enabled = !busy,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next),
                    )
                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it; error = "" },
                        label = { Text("Password") },
                        visualTransformation = PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        enabled = !busy,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                    )
                    if (error.isNotBlank()) Text(error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    Button(
                        onClick = {
                            if (email.isBlank() || password.isBlank()) { error = "Email and password are required"; return@Button }
                            busy = true
                            scope.launch {
                                val result = withContext(Dispatchers.IO) { auth.login(email.trim(), password) }
                                if (result.isSuccess) onLoggedIn() else { error = result.exceptionOrNull()?.message ?: "Login failed"; busy = false }
                            }
                        },
                        enabled = !busy,
                        modifier = Modifier.fillMaxWidth(),
                    ) { if (busy) { CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp) } else Text("LOGIN") }
                }
            }
            Text("Your entries are stored locally first and synced when connectivity is available.", style = MaterialTheme.typography.labelSmall)
        }
    }
}
