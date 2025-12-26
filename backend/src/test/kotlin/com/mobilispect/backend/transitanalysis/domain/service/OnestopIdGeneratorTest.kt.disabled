package com.mobilispect.backend.transitanalysis.domain.service

import com.mobilispect.backend.feed.model.ids.FeedId
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Test for OnestopIdGenerator service.
 *
 * Validates:
 * - Geohash extraction from feed Onestop ID
 * - Stop name normalization
 * - Full Stop Onestop ID generation
 * - Edge cases (special characters, empty names, etc.)
 */
class OnestopIdGeneratorTest {

    private val generator = OnestopIdGeneratorImpl()

    @Test
    fun `should generate stop ID with geohash from feed ID`() {
        val feedId = FeedId("f-9q8y-sfmta")
        val stopId = generator.generateStopId(
            feedId = feedId,
            gtfsStopId = "1234",
            name = "Market St & 5th St",
            lat = 37.7749,
            lon = -122.4194
        )

        assertTrue(stopId.value.startsWith("s-9q8y-"))
        assertTrue(stopId.value.contains("market"))
    }

    @Test
    fun `should normalize stop name to lowercase alphanumeric with tildes`() {
        val normalized = generator.normalizeNameForOnestopId("Market St & 5th St")
        assertEquals("market~st~5th~st", normalized)
    }

    @Test
    fun `should handle stop names with special characters`() {
        val normalized = generator.normalizeNameForOnestopId("Gare de l'Est (Paris)")
        assertEquals("gare~de~l~est~paris", normalized)
    }

    @Test
    fun `should handle stop names with multiple consecutive special chars`() {
        val normalized = generator.normalizeNameForOnestopId("Union   Station -- Main  Entrance")
        assertEquals("union~station~main~entrance", normalized)
    }

    @Test
    fun `should handle stop names with accented characters`() {
        val normalized = generator.normalizeNameForOnestopId("Métro Château")
        assertEquals("m~tro~ch~teau", normalized)
    }

    @Test
    fun `should truncate very long stop names`() {
        val longName = "A".repeat(200)
        val normalized = generator.normalizeNameForOnestopId(longName)
        assertTrue(normalized.length <= 100, "Normalized name should be truncated to max 100 chars")
    }

    @Test
    fun `should generate complete stop Onestop ID`() {
        val feedId = FeedId("f-9q8y-sfmta")
        val stopId = generator.generateStopId(
            feedId = feedId,
            gtfsStopId = "1234",
            name = "Market St",
            lat = 37.7749,
            lon = -122.4194
        )

        assertEquals("s-9q8y-market~st", stopId.value)
    }

    @Test
    fun `should handle feed IDs with complex geohashes`() {
        val feedId = FeedId("f-dr5reg-wmata")
        val stopId = generator.generateStopId(
            feedId = feedId,
            gtfsStopId = "A01",
            name = "Metro Center",
            lat = 38.8983,
            lon = -77.0281
        )

        assertTrue(stopId.value.startsWith("s-dr5reg-"))
        assertTrue(stopId.value.endsWith("metro~center"))
    }

    @Test
    fun `should handle single-word stop names`() {
        val normalized = generator.normalizeNameForOnestopId("Terminal")
        assertEquals("terminal", normalized)
    }

    @Test
    fun `should handle stop names with numbers`() {
        val normalized = generator.normalizeNameForOnestopId("Platform 9 3/4")
        assertEquals("platform~9~3~4", normalized)
    }

    @Test
    fun `should extract geohash from feed ID with extra components`() {
        val feedId = FeedId("f-9q9-caltrain~local")
        val stopId = generator.generateStopId(
            feedId = feedId,
            gtfsStopId = "SF",
            name = "San Francisco",
            lat = 37.7765,
            lon = -122.3918
        )

        assertTrue(stopId.value.startsWith("s-9q9-"))
    }
}
