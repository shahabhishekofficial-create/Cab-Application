package com.caboperations.driver.ocr

import android.content.Context
import android.net.Uri
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.tasks.await
import kotlin.math.abs

data class OdometerOcrResult(
    val reading: Double?,
    val confidence: Float,
    val rawText: String
)

/**
 * Runs entirely on-device. The confidence value is a conservative quality score
 * derived from the OCR candidate; ML Kit's Text API does not expose one overall
 * document confidence value for this use case.
 */
class OdometerOcrEngine(private val context: Context) {
    private val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

    suspend fun recognize(uri: Uri): OdometerOcrResult {
        return runCatching {
            val image = InputImage.fromFilePath(context, uri)
            val result = recognizer.process(image).await()
            val rawText = result.text
            val candidate = extractCandidate(rawText)
            OdometerOcrResult(candidate?.first, candidate?.second ?: 0f, rawText)
        }.getOrElse {
            OdometerOcrResult(null, 0f, "")
        }
    }

    private fun extractCandidate(text: String): Pair<Double, Float>? {
        val candidates = text.lines()
            .flatMap { line ->
                Regex("\\d+(?:[.,]\\d+)?").findAll(line.replace(" ", "")).map { it.value }.toList()
            }
            .mapNotNull { token ->
                token.replace(',', '.').toDoubleOrNull()?.let { value -> token to value }
            }
            .filter { (_, value) -> value in 0.0..999_999.9 }

        if (candidates.isEmpty()) return null

        // Prefer a typical vehicle-odometer candidate: at least 3 digits and
        // not a tiny isolated number likely to be a unit/label.
        val preferred = candidates.firstOrNull { (token, value) ->
            token.filter { it.isDigit() }.length >= 3 && value >= 100.0
        } ?: candidates.maxByOrNull { it.second }

        return preferred?.let { (token, value) ->
            val digits = token.count { it.isDigit() }
            val quality = when {
                digits >= 4 -> 0.92f
                digits == 3 -> 0.82f
                else -> 0.65f
            }
            value to quality
        }
    }
}

object OdometerVerifier {
    enum class Decision { PASS, REVIEW }

    fun compare(manualReading: Double, ocrReading: Double?, confidence: Float, toleranceKm: Double = 1.0): Decision {
        if (ocrReading == null || confidence < 0.70f) return Decision.REVIEW
        return if (abs(manualReading - ocrReading) <= toleranceKm) Decision.PASS else Decision.REVIEW
    }
}
