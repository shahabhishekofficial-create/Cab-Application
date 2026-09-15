package com.caboperations.driver.ui

sealed interface OdometerValidationResult {
    data object Empty : OdometerValidationResult
    data object Invalid : OdometerValidationResult
    data class Valid(val value: Double) : OdometerValidationResult
    data class Regression(val entered: Double, val current: Double) : OdometerValidationResult
}

object OdometerValidation {
    fun validateStartingOdometer(input: String, currentOdometer: Double?): OdometerValidationResult {
        val value = input.toDoubleOrNull() ?: return if (input.isBlank()) OdometerValidationResult.Empty else OdometerValidationResult.Invalid
        if (!value.isFinite() || value < 0.0) return OdometerValidationResult.Invalid
        if (currentOdometer != null && currentOdometer.isFinite() && value < currentOdometer) {
            return OdometerValidationResult.Regression(value, currentOdometer)
        }
        return OdometerValidationResult.Valid(value)
    }

    fun format(value: Double): String = if (value % 1.0 == 0.0) value.toLong().toString() else "%.2f".format(value)
}
