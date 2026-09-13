package com.caboperations.driver.ui

import android.content.Intent
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
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.caboperations.driver.BuildConfig
import com.caboperations.driver.auth.AuthRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.net.SocketTimeoutException
import java.net.UnknownHostException

@Composable
fun LoginScreen(auth: AuthRepository, onLoggedIn: () -> Unit) {
    val context = LocalContext.current; var email by remember { mutableStateOf("") }; var password by remember { mutableStateOf("") }; var busy by remember { mutableStateOf(false) }; var error by remember { mutableStateOf("") }; val scope = rememberCoroutineScope()
    val fieldColors = OutlinedTextFieldDefaults.colors(focusedTextColor = CabText, unfocusedTextColor = CabText, disabledTextColor = CabGray, focusedLabelColor = CabAccent, unfocusedLabelColor = CabGray, focusedBorderColor = CabAccent, unfocusedBorderColor = Color(0xFF4A4A54), cursorColor = CabAccent, focusedPlaceholderColor = CabGray, unfocusedPlaceholderColor = CabGray)
    fun friendlyError(t: Throwable): String { val m = t.message.orEmpty().lowercase(); return when { t is SocketTimeoutException || m.contains("timeout") -> "Sign-in timed out. Check your internet connection and try again."; t is UnknownHostException || m.contains("unable to resolve") || m.contains("network") -> "Network error. Check your internet connection and try again."; m.contains("invalid login") || m.contains("invalid credentials") || m.contains("password") -> "Email or password is incorrect."; m.contains("not found") -> "Account not found. Check the email address."; else -> t.message ?: "Sign-in failed. Please try again." } }

    Box(Modifier.fillMaxSize().background(CabPageBackground).imePadding()) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 28.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Spacer(Modifier.height(20.dp)); Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) { Surface(shape = RoundedCornerShape(14.dp), color = CabAccent) { Text("CAB", Modifier.padding(horizontal = 14.dp, vertical = 9.dp), color = Color.Black, fontWeight = FontWeight.ExtraBold) }; Text("Cab Driver", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = CabText) }
            Text("Welcome back", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold, color = CabText); Text("Sign in to manage your cab day", color = CabGray); Text("App version ${BuildConfig.VERSION_NAME}", style = MaterialTheme.typography.labelMedium, color = CabGray)
            CabCard {
                Text("Driver login", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = CabText)
                OutlinedButton(onClick = { error = ""; auth.googleAuthorizeUrl().onSuccess { url -> runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply { addCategory(Intent.CATEGORY_BROWSABLE) }) }.onFailure { error = it.message ?: "Unable to open Google sign in" } }.onFailure { error = it.message ?: "Google sign in unavailable" } }, enabled = !busy, modifier = Modifier.fillMaxWidth().height(50.dp), shape = RoundedCornerShape(15.dp), colors = ButtonDefaults.outlinedButtonColors(contentColor = CabText)) { Text("Continue with Google", fontWeight = FontWeight.SemiBold) }
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) { HorizontalDivider(Modifier.weight(1f), color = Color(0xFF34343C)); Text("  or  ", style = MaterialTheme.typography.labelSmall, color = CabGray); HorizontalDivider(Modifier.weight(1f), color = Color(0xFF34343C)) }
                OutlinedTextField(email, { email = it; error = "" }, label = { Text("Email address") }, placeholder = { Text("name@example.com") }, modifier = Modifier.fillMaxWidth(), singleLine = true, enabled = !busy, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next), shape = RoundedCornerShape(14.dp), colors = fieldColors)
                OutlinedTextField(password, { password = it; error = "" }, label = { Text("Password") }, placeholder = { Text("Enter your password") }, visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth(), singleLine = true, enabled = !busy, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done), shape = RoundedCornerShape(14.dp), colors = fieldColors)
                if (error.isNotBlank()) Surface(shape = RoundedCornerShape(12.dp), color = Color(0xFF3A2024)) { Text(error, Modifier.fillMaxWidth().padding(12.dp), color = Color(0xFFFF8F9A)) }
                CabPrimaryButton(if (busy) "Signing in…" else "Sign in", enabled = !busy) {
                    if (email.isBlank() || password.isBlank()) { error = "Enter your email and password"; return@CabPrimaryButton }
                    busy = true
                    scope.launch {
                        val result = runCatching { withTimeout(15_000L) { withContext(Dispatchers.IO) { auth.login(email.trim(), password) } } }
                        if (result.isSuccess && result.getOrThrow().isSuccess) { busy = false; error = ""; onLoggedIn() } else { val failure = if (result.isFailure) result.exceptionOrNull() else result.getOrThrow().exceptionOrNull(); error = failure?.let(::friendlyError) ?: "Sign-in failed. Please check your details and try again."; busy = false }
                    }
                }
            }
            Surface(shape = RoundedCornerShape(16.dp), color = Color(0xFF202A16)) { Text("Works offline • your saved entries stay on your phone until they sync.", Modifier.padding(14.dp), color = CabAccent, style = MaterialTheme.typography.bodySmall) }
        }
    }
}
