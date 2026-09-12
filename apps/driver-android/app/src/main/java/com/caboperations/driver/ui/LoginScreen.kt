package com.caboperations.driver.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
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

    Box(Modifier.fillMaxSize().background(CabPageBackground).imePadding()) {
        Column(
            modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Spacer(Modifier.height(20.dp))
            Surface(shape = RoundedCornerShape(24.dp), color = CabPurpleSoft) {
                Text("CAB", modifier = Modifier.padding(horizontal = 20.dp, vertical = 14.dp), color = CabPurple, fontWeight = FontWeight.ExtraBold)
            }
            Text("Welcome back", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
            Text("Sign in to manage your cab day", color = CabGray)
            Text("App version 1.1", style = MaterialTheme.typography.labelMedium, color = CabGray)

            CabCard {
                Text("Driver login", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                OutlinedButton(
                    onClick = {
                        error = ""
                        auth.googleAuthorizeUrl().onSuccess { url ->
                            runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply { addCategory(Intent.CATEGORY_BROWSABLE) }) }
                                .onFailure { error = it.message ?: "Unable to open Google sign in" }
                        }.onFailure { error = it.message ?: "Google sign in unavailable" }
                    }, enabled = !busy, modifier = Modifier.fillMaxWidth().height(50.dp), shape = RoundedCornerShape(15.dp)
                ) { Text("Continue with Google", fontWeight = FontWeight.SemiBold) }

                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    HorizontalDivider(Modifier.weight(1f)); Text("  or  ", style = MaterialTheme.typography.labelSmall, color = CabGray); HorizontalDivider(Modifier.weight(1f))
                }
                OutlinedTextField(email, { email = it; error = "" }, label = { Text("Email address") }, modifier = Modifier.fillMaxWidth(), singleLine = true, enabled = !busy, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next), shape = RoundedCornerShape(14.dp))
                OutlinedTextField(password, { password = it; error = "" }, label = { Text("Password") }, visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth(), singleLine = true, enabled = !busy, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done), shape = RoundedCornerShape(14.dp))
                if (error.isNotBlank()) {
                    Surface(shape = RoundedCornerShape(12.dp), color = Color(0xFFFFE7E7)) { Text(error, modifier = Modifier.fillMaxWidth().padding(12.dp), color = MaterialTheme.colorScheme.error) }
                }
                CabPrimaryButton(if (busy) "Signing in…" else "Sign in", enabled = !busy) {
                    if (email.isBlank() || password.isBlank()) { error = "Enter your email and password"; return@CabPrimaryButton }
                    busy = true
                    scope.launch {
                        val result = withContext(Dispatchers.IO) { auth.login(email.trim(), password) }
                        if (result.isSuccess) onLoggedIn() else { error = result.exceptionOrNull()?.message ?: "Login failed. Check your details and try again."; busy = false }
                    }
                }
            }

            Surface(shape = RoundedCornerShape(16.dp), color = CabGreenSoft) {
                Text("Works offline • your saved entries stay on your phone until they sync.", modifier = Modifier.padding(14.dp), color = CabGreen, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}
