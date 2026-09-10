package com.caboperations.driver.location

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LocationValidatorTest {
    @Test fun acceptsReasonableGps() {
        assertTrue(LocationSnapshot(21.1652, 72.7799, 19f, 1L).isUsable())
    }

    @Test fun rejectsPoorAccuracy() {
        assertFalse(LocationSnapshot(21.1652, 72.7799, 150f, 1L).isUsable())
    }

    @Test fun rejectsInvalidCoordinates() {
        assertFalse(LocationSnapshot(95.0, 72.0, 10f, 1L).isUsable())
    }
}
