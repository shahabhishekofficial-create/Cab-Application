package com.caboperations.driver.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OdometerValidationTest {
    @Test fun acceptsReadingAtAuthoritativeValue() {
        assertTrue(OdometerValidation.validateStartingOdometer("12345", 12345.0) is OdometerValidationResult.Valid)
    }

    @Test fun acceptsReadingAboveAuthoritativeValue() {
        assertTrue(OdometerValidation.validateStartingOdometer("12345.5", 12345.0) is OdometerValidationResult.Valid)
    }

    @Test fun rejectsRegressionAgainstAuthoritativeValue() {
        val result = OdometerValidation.validateStartingOdometer("12344", 12345.0)
        assertEquals(OdometerValidationResult.Regression(12344.0, 12345.0), result)
    }

    @Test fun rejectsNegativeAndMalformedInput() {
        assertTrue(OdometerValidation.validateStartingOdometer("-1", 0.0) is OdometerValidationResult.Invalid)
        assertTrue(OdometerValidation.validateStartingOdometer("abc", 0.0) is OdometerValidationResult.Invalid)
    }
}
