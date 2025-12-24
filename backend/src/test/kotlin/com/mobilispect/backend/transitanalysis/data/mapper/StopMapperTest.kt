package com.mobilispect.backend.transitanalysis.data.mapper

import com.mobilispect.backend.feed.data.entity.FeedEntity
import com.mobilispect.backend.feed.model.ids.FeedId
import com.mobilispect.backend.transitanalysis.data.entity.StopEntity
import com.mobilispect.backend.transitanalysis.domain.model.Stop
import com.mobilispect.backend.transitanalysis.domain.model.ids.StopId
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.time.Instant

/**
 * Test for StopMapper bidirectional conversion.
 *
 * Validates:
 * - Entity to domain conversion
 * - Domain to entity conversion
 * - Null handling for optional fields
 * - Proper value class wrapping/unwrapping
 */
class StopMapperTest {

    private val mapper = StopMapper()

    @Test
    fun `toDomain should convert entity to domain model`() {
        val feedEntity = createTestFeedEntity()
        val now = Instant.now()
        val entity = StopEntity(
            stopOnestopId = "s-9q8y-market~st",
            feed = feedEntity,
            gtfsStopId = "1234",
            name = "Market St Station",
            latitude = 37.7749,
            longitude = -122.4194,
            stopCode = "MS01",
            stopDesc = "Main station on Market Street",
            zoneId = "A",
            stopUrl = "https://example.com/stops/1234",
            locationType = 1,
            parentStation = null,
            active = true,
            firstSeen = now,
            lastSeen = now,
            createdAt = now,
            updatedAt = now
        )

        val domain = mapper.toDomain(entity)

        assertEquals(StopId("s-9q8y-market~st"), domain.stopOnestopId)
        assertEquals(FeedId("f-9q8y-bart"), domain.feedId)
        assertEquals("1234", domain.gtfsStopId)
        assertEquals("Market St Station", domain.name)
        assertEquals(37.7749, domain.latitude)
        assertEquals(-122.4194, domain.longitude)
        assertEquals("MS01", domain.stopCode)
        assertEquals("Main station on Market Street", domain.stopDesc)
        assertEquals("A", domain.zoneId)
        assertEquals("https://example.com/stops/1234", domain.stopUrl)
        assertEquals(1, domain.locationType)
        assertNull(domain.parentStation)
        assertTrue(domain.active)
        assertEquals(now, domain.firstSeen)
        assertEquals(now, domain.lastSeen)
        assertEquals(now, domain.createdAt)
        assertEquals(now, domain.updatedAt)
    }

    @Test
    fun `toDomain should handle null optional fields`() {
        val feedEntity = createTestFeedEntity()
        val now = Instant.now()
        val entity = StopEntity(
            stopOnestopId = "s-9q8y-market~st",
            feed = feedEntity,
            gtfsStopId = "1234",
            name = "Market St Station",
            latitude = 37.7749,
            longitude = -122.4194,
            stopCode = null,
            stopDesc = null,
            zoneId = null,
            stopUrl = null,
            locationType = null,
            parentStation = null,
            active = true,
            firstSeen = now,
            lastSeen = now
        )

        val domain = mapper.toDomain(entity)

        assertNull(domain.stopCode)
        assertNull(domain.stopDesc)
        assertNull(domain.zoneId)
        assertNull(domain.stopUrl)
        assertNull(domain.locationType)
        assertNull(domain.parentStation)
    }

    @Test
    fun `toEntity should convert domain model to entity`() {
        val feedEntity = createTestFeedEntity()
        val now = Instant.now()
        val domain = Stop(
            stopOnestopId = StopId("s-9q8y-market~st"),
            feedId = FeedId("f-9q8y-bart"),
            gtfsStopId = "1234",
            name = "Market St Station",
            latitude = 37.7749,
            longitude = -122.4194,
            stopCode = "MS01",
            stopDesc = "Main station on Market Street",
            zoneId = "A",
            stopUrl = "https://example.com/stops/1234",
            locationType = 1,
            parentStation = null,
            active = true,
            firstSeen = now,
            lastSeen = now,
            createdAt = now,
            updatedAt = now
        )

        val entity = mapper.toEntity(domain, feedEntity)

        assertEquals("s-9q8y-market~st", entity.stopOnestopId)
        assertEquals(feedEntity, entity.feed)
        assertEquals("1234", entity.gtfsStopId)
        assertEquals("Market St Station", entity.name)
        assertEquals(37.7749, entity.latitude)
        assertEquals(-122.4194, entity.longitude)
        assertEquals("MS01", entity.stopCode)
        assertEquals("Main station on Market Street", entity.stopDesc)
        assertEquals("A", entity.zoneId)
        assertEquals("https://example.com/stops/1234", entity.stopUrl)
        assertEquals(1, entity.locationType)
        assertNull(entity.parentStation)
        assertTrue(entity.active)
        assertEquals(now, entity.firstSeen)
        assertEquals(now, entity.lastSeen)
        assertEquals(now, entity.createdAt)
        assertEquals(now, entity.updatedAt)
    }

    @Test
    fun `toEntity should handle null optional fields`() {
        val feedEntity = createTestFeedEntity()
        val now = Instant.now()
        val domain = Stop(
            stopOnestopId = StopId("s-9q8y-market~st"),
            feedId = FeedId("f-9q8y-bart"),
            gtfsStopId = "1234",
            name = "Market St Station",
            latitude = 37.7749,
            longitude = -122.4194,
            stopCode = null,
            stopDesc = null,
            zoneId = null,
            stopUrl = null,
            locationType = null,
            parentStation = null,
            active = true,
            firstSeen = now,
            lastSeen = now
        )

        val entity = mapper.toEntity(domain, feedEntity)

        assertNull(entity.stopCode)
        assertNull(entity.stopDesc)
        assertNull(entity.zoneId)
        assertNull(entity.stopUrl)
        assertNull(entity.locationType)
        assertNull(entity.parentStation)
    }

    @Test
    fun `bidirectional conversion should preserve data`() {
        val feedEntity = createTestFeedEntity()
        val now = Instant.now()
        val originalDomain = Stop(
            stopOnestopId = StopId("s-9q8y-market~st"),
            feedId = FeedId("f-9q8y-bart"),
            gtfsStopId = "1234",
            name = "Market St Station",
            latitude = 37.7749,
            longitude = -122.4194,
            stopCode = "MS01",
            stopDesc = "Main station on Market Street",
            zoneId = "A",
            stopUrl = "https://example.com/stops/1234",
            locationType = 1,
            parentStation = "parent-123",
            active = true,
            firstSeen = now,
            lastSeen = now,
            createdAt = now,
            updatedAt = now
        )

        val entity = mapper.toEntity(originalDomain, feedEntity)
        val reconvertedDomain = mapper.toDomain(entity)

        assertEquals(originalDomain.stopOnestopId, reconvertedDomain.stopOnestopId)
        assertEquals(originalDomain.gtfsStopId, reconvertedDomain.gtfsStopId)
        assertEquals(originalDomain.name, reconvertedDomain.name)
        assertEquals(originalDomain.latitude, reconvertedDomain.latitude)
        assertEquals(originalDomain.longitude, reconvertedDomain.longitude)
        assertEquals(originalDomain.stopCode, reconvertedDomain.stopCode)
        assertEquals(originalDomain.stopDesc, reconvertedDomain.stopDesc)
        assertEquals(originalDomain.zoneId, reconvertedDomain.zoneId)
        assertEquals(originalDomain.stopUrl, reconvertedDomain.stopUrl)
        assertEquals(originalDomain.locationType, reconvertedDomain.locationType)
        assertEquals(originalDomain.parentStation, reconvertedDomain.parentStation)
        assertEquals(originalDomain.active, reconvertedDomain.active)
    }

    private fun createTestFeedEntity(): FeedEntity =
        FeedEntity(
            feedOnestopId = "f-9q8y-bart",
            name = "BART",
            specType = com.mobilispect.backend.feed.model.FeedSpecType.GTFS,
            downloadUrl = "https://example.com/gtfs.zip",
            status = com.mobilispect.backend.feed.model.FeedStatus.ACTIVE
        )
}
