package com.caboperations.driver.ocr

data class OdometerOcrResult(
    val reading: Double?,
    val confidence: Float,
    val rawText: String
)

object OdometerVerifier {
    enum class Decision { PASS, REVIEW }

    fun compare(manualReading: Double, ocrReading: Double?, confidence: Float, toleranceKm: Double = 1.0): Decision {
        if (ocrReading == null || confidence < 0.70f) return Decision.REVIEW
        return if (kotlin.math.abs(manualReading - ocrReading) <= toleranceKm) Decision.PASS else Decision.REVIEW
    }
}
