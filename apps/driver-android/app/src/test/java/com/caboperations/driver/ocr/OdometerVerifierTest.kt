package com.caboperations.driver.ocr

import org.junit.Assert.assertEquals
import org.junit.Test

class OdometerVerifierTest {
    @Test
    fun matchingReadingPassesWithinTolerance() {
        assertEquals(
            OdometerVerifier.Decision.PASS,
            OdometerVerifier.compare(12543.2, 12543.9, 0.92f, toleranceKm = 1.0)
        )
    }

    @Test
    fun exactToleranceBoundaryPasses() {
        assertEquals(
            OdometerVerifier.Decision.PASS,
            OdometerVerifier.compare(12543.2, 12544.2, 0.92f, toleranceKm = 1.0)
        )
    }

    @Test
    fun mismatchRequiresReview() {
        assertEquals(
            OdometerVerifier.Decision.REVIEW,
            OdometerVerifier.compare(12543.2, 12546.0, 0.92f, toleranceKm = 1.0)
        )
    }

    @Test
    fun lowConfidenceRequiresReview() {
        assertEquals(
            OdometerVerifier.Decision.REVIEW,
            OdometerVerifier.compare(12543.2, 12543.2, 0.69f, toleranceKm = 1.0)
        )
    }

    @Test
    fun missingOcrRequiresReview() {
        assertEquals(
            OdometerVerifier.Decision.REVIEW,
            OdometerVerifier.compare(12543.2, null, 0.95f, toleranceKm = 1.0)
        )
    }
}
