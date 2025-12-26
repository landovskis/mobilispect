package com.mobilispect.backend.transitanalysis.domain.model.ids

import com.mobilispect.backend.stop.domain.model.ids.StopId
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

/**
 * Test for StopId value class.
 *
 * Validates:
 * - Value class construction and validation
 * - toString() override
 * - from() factory method
 * - Blank/null value handling
 */
class StopIdTest {

    @Test
    fun `should create StopId with valid value`() {
        val stopId = StopId("s-9q8y-market~st")
        assertEquals("s-9q8y-market~st", stopId.value)
    }

    @Test
    fun `should reject blank StopId`() {
        assertThrows<IllegalArgumentException> {
            StopId("")
        }
    }

    @Test
    fun `should reject whitespace-only StopId`() {
        assertThrows<IllegalArgumentException> {
            StopId("   ")
        }
    }

    @Test
    fun `toString should return the value`() {
        val stopId = StopId("s-9q8y-market~st")
        assertEquals("s-9q8y-market~st", stopId.toString())
    }

    @Test
    fun `from should create StopId from non-blank string`() {
        val stopId = StopId.from("s-9q8y-market~st")
        assertEquals("s-9q8y-market~st", stopId?.value)
    }

    @Test
    fun `from should return null for null input`() {
        val stopId = StopId.from(null)
        assertNull(stopId)
    }

    @Test
    fun `from should return null for blank input`() {
        val stopId = StopId.from("")
        assertNull(stopId)
    }

    @Test
    fun `from should return null for whitespace input`() {
        val stopId = StopId.from("   ")
        assertNull(stopId)
    }

    @Test
    fun `should support Transitland Onestop ID format`() {
        val stopId = StopId("s-dr5r-union~station")
        assertEquals("s-dr5r-union~station", stopId.value)
    }
}
