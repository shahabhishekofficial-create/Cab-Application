package com.caboperations.driver.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

val CabPageBackground = Color(0xFFF6F7F5)
val CabPurple = Color(0xFF5B3FA8)
val CabPurpleSoft = Color(0xFFEDE8FA)
val CabGreen = Color(0xFF247A4A)
val CabGreenSoft = Color(0xFFE5F4EA)
val CabAmber = Color(0xFF9A6200)
val CabAmberSoft = Color(0xFFFFF1D6)
val CabInk = Color(0xFF202124)
val CabGray = Color(0xFF6B6E73)

@Composable
fun CabScreen(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Top,
    ) { content() }
}

@Composable
fun CabHeader(title: String, subtitle: String? = null, onBack: (() -> Unit)? = null) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (onBack != null) {
            TextButton(onClick = onBack, contentPadding = PaddingValues(horizontal = 6.dp)) { Text("Back") }
            Spacer(Modifier.width(6.dp))
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = CabInk)
            if (!subtitle.isNullOrBlank()) Text(subtitle, style = MaterialTheme.typography.bodySmall, color = CabGray)
        }
    }
}

@Composable
fun CabCard(modifier: Modifier = Modifier, color: Color = Color.White, content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = color),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) { Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) { content() } }
}

@Composable
fun CabSectionLabel(text: String) {
    Text(text, style = MaterialTheme.typography.labelMedium, color = CabGray, fontWeight = FontWeight.Bold)
}

@Composable
fun CabStep(number: String, title: String, done: Boolean, content: @Composable ColumnScope.() -> Unit) {
    CabCard(color = if (done) CabGreenSoft else Color.White) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Surface(shape = RoundedCornerShape(50), color = if (done) CabGreen else CabPurpleSoft) {
                Text(if (done) "✓" else number, modifier = Modifier.padding(horizontal = 11.dp, vertical = 7.dp), color = if (done) CabGreen else CabPurple, fontWeight = FontWeight.Bold)
            }
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(if (done) "Completed" else "Required", style = MaterialTheme.typography.bodySmall, color = if (done) CabGreen else CabGray)
            }
        }
        if (!done) content()
    }
}

@Composable
fun CabPrimaryButton(text: String, enabled: Boolean = true, onClick: () -> Unit) {
    Button(onClick = onClick, enabled = enabled, modifier = Modifier.fillMaxWidth().height(52.dp), shape = RoundedCornerShape(16.dp)) {
        Text(text, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun CabSecondaryButton(text: String, enabled: Boolean = true, onClick: () -> Unit) {
    OutlinedButton(onClick = onClick, enabled = enabled, modifier = Modifier.fillMaxWidth().height(50.dp), shape = RoundedCornerShape(15.dp)) {
        Text(text, fontWeight = FontWeight.SemiBold)
    }
}
