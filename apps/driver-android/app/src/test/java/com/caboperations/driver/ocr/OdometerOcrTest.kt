package com.caboperations.driver.ocr

import org.junit.Assert.assertEquals
import org.junit.Test

class OdometerOcrTest {
    @Test fun matchingReadingPasses() {
        assertEquals(OdometerVerifier.Decision.PASS, OdometerVerifier.compare(12543.2, 12543.8, 0.95f))
    }

    @Test fun mismatchRequiresReview() {
        assertEquals(OdometerVerifier.Decision.REVIEW, OdometerVerifier.compare(12543.2, 12547.0, 0.95f))
    }

    @Test fun lowConfidenceRequiresReview() {
        assertEquals(OdometerVerifier.Decision.REVIEW, OdometerVerifier.compare(12543.2, 12543.2, 0.60f))
    }

    @Test fun missingOcrRequiresReview() {
        assertEquals(OdometerVerifier.Decision.REVIEW, OdometerVerifier.compare(12543.2, null, 0f))
    }
}
