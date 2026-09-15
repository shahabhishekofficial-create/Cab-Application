package com.caboperations.driver.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.weight
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun OdometerNumpad(
    value: String,
    currentOdometer: Double?,
    onValueChange: (String) -> Unit,
    onNext: () -> Unit,
    onBack: () -> Unit
) {
    val validation = OdometerValidation.validateStartingOdometer(value, currentOdometer)
    Column(Modifier.fillMaxWidth().background(Color(0xFF121212))) {
        Column(
            Modifier.fillMaxWidth().weight(0.40f).padding(horizontal = 20.dp, vertical = 18.dp),
            verticalArrangement = Arrangement.Center
        ) {
            Text("Current Odometer (km)", color = Color(0xFF9E9E9E), style = MaterialTheme.typography.labelLarge)
            Spacer(Modifier.height(8.dp))
            Text(
                value.ifBlank { "0" },
                color = Color.White,
                fontSize = 96.sp,
                lineHeight = 100.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                maxLines = 1
            )
            Spacer(Modifier.height(12.dp))
            when (validation) {
                is OdometerValidationResult.Regression ->
                    Text(
                        "Entered reading (${OdometerValidation.format(validation.entered)}) is lower than last recorded reading (${OdometerValidation.format(validation.current)})",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.fillMaxWidth().background(Color(0xFF8B1E1E), RoundedCornerShape(12.dp)).padding(14.dp)
                    )
                OdometerValidationResult.Invalid -> Text("Enter a valid non-negative odometer reading", color = Color(0xFFFF6B6B))
                else -> Unit
            }
        }

        Column(Modifier.fillMaxWidth().weight(0.60f).padding(horizontal = 12.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            val rows = listOf(listOf("1", "2", "3"), listOf("4", "5", "6"), listOf("7", "8", "9"), listOf("CLEAR", "0", "BACKSPACE"))
            rows.forEach { row ->
                Row(Modifier.fillMaxWidth().weight(1f), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    row.forEach { key ->
                        NumpadKey(
                            key = key,
                            modifier = Modifier.weight(1f),
                            onClick = {
                                when (key) {
                                    "CLEAR" -> onValueChange("")
                                    "BACKSPACE" -> onValueChange(value.dropLast(1))
                                    else -> if (value.length < 9) onValueChange((value + key).trimStart('0').ifBlank { "0" })
                                }
                            }
                        )
                    }
                }
            }
            NumpadKey("NEXT / CAMERA", Modifier.fillMaxWidth().height(72.dp), enabled = validation is OdometerValidationResult.Valid, onClick = onNext)
            Text("BACK", color = Color(0xFF9E9E9E), modifier = Modifier.fillMaxWidth().clickable(onClick = onBack).padding(10.dp), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        }
    }
}

@Composable
private fun NumpadKey(key: String, modifier: Modifier, enabled: Boolean = true, onClick: () -> Unit) {
    val background = if (enabled) Color(0xFF242424) else Color(0xFF181818)
    val text = if (enabled) Color.White else Color(0xFF666666)
    Box(
        modifier = modifier
            .background(background, RoundedCornerShape(16.dp))
            .border(1.dp, Color(0xFF3A3A3A), RoundedCornerShape(16.dp))
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(key, color = text, fontSize = if (key.length > 2) 18.sp else 28.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
    }
}
