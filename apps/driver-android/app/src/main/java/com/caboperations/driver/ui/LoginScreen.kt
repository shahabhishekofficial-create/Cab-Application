package com.caboperations.driver.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.caboperations.driver.auth.AuthRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun LoginScreen(auth: AuthRepository, onLoggedIn: () -> Unit) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()

    Column(modifier = Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text("Driver Login")

        OutlinedButton(
            onClick = {
                error = ""
                auth.googleAuthorizeUrl().onSuccess { url ->
                    runCatching {
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                            addCategory(Intent.CATEGORY_BROWSABLE)
                        }
                        val context = androidx.compose.ui.platform.LocalContext.current
                    }.onFailure { error = it.message ?: "GOOGLE_LOGIN_FAILED" }
                }.onFailure { error = it.message ?: "GOOGLE_LOGIN_FAILED" }
            },
            enabled = !busy,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("CONTINUE WITH GOOGLE")
        }

        HorizontalDivider()
        Text("Or sign in with email and password")

        OutlinedTextField(
            email,
            { email = it; error = "" },
            label = { Text("Email") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
        )
        OutlinedTextField(
            password,
            { password = it; error = "" },
            label = { Text("Password") },
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
        )
        if (error.isNotBlank()) Text(error)
        Button(
            onClick = {
                if (email.isBlank() || password.isBlank()) { error = "Email and password are required"; return@Button }
                busy = true
                scope.launch {
                    val result = withContext(Dispatchers.IO) { auth.login(email.trim(), password) }
                    if (result.isSuccess) onLoggedIn() else { error = result.exceptionOrNull()?.message ?: "LOGIN_FAILED"; busy = false }
                }
            },
            enabled = !busy,
            modifier = Modifier.fillMaxWidth(),
        ) {
            if (busy) CircularProgressIndicator() else Text("LOGIN")
        }
    }
}
