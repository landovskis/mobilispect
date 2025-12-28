package com.mobilispect.backend.feed.data.mapper

import com.mobilispect.backend.feed.data.entity.FeedEntity
import com.mobilispect.backend.feed.domain.model.Feed
import com.mobilispect.backend.feed.domain.model.ids.FeedId
import com.mobilispect.backend.feed.model.FeedSpecType
import com.mobilispect.backend.feed.model.FeedStatus
import com.mobilispect.backend.feed.model.ids.RegionId
import com.mobilispect.backend.region.data.MetropolitanRegionEntity
import java.time.Instant
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class FeedMapperTest {

  private val mapper = FeedMapper()

  @Test
  fun `toDomain should convert entity to domain model with all fields`() {
    // Given
    val now = Instant.now()
    val regionEntity1 =
      MetropolitanRegionEntity(regionOnestopId = "r-dpz8-sf", name = "San Francisco Bay Area")
    val regionEntity2 =
      MetropolitanRegionEntity(regionOnestopId = "r-9q5-sandiego", name = "San Diego")

    val entity =
      FeedEntity(
        feedOnestopId = "f-9q8y-sfmta",
        name = "SF Muni",
        specType = FeedSpecType.GTFS,
        downloadUrl = "https://example.com/feed.zip",
        staticFeedUrl = "https://example.com/static.zip",
        realtimeFeedUrl = "https://example.com/realtime",
        operatorName = "San Francisco Municipal Transportation Agency",
        currentVersionSha1 = "abc123def456",
        status = FeedStatus.ACTIVE,
        lastCheckedAt = now,
        lastUpdatedAt = now,
        lastDiscoveredAt = now,
        createdAt = now,
        updatedAt = now,
      )
    entity.regions.add(regionEntity1)
    entity.regions.add(regionEntity2)

    // When
    val domain = mapper.toDomain(entity)

    // Then
    assertEquals(FeedId("f-9q8y-sfmta"), domain.feedId)
    assertEquals("SF Muni", domain.name)
    assertEquals("San Francisco Municipal Transportation Agency", domain.operatorName)
    assertEquals(FeedSpecType.GTFS, domain.specType)
    assertEquals("https://example.com/feed.zip", domain.downloadUrl)
    assertEquals("https://example.com/static.zip", domain.staticFeedUrl)
    assertEquals("https://example.com/realtime", domain.realtimeFeedUrl)
    assertEquals("abc123def456", domain.currentVersionSha1)
    assertEquals(FeedStatus.ACTIVE, domain.status)
    assertEquals(setOf(RegionId("r-dpz8-sf"), RegionId("r-9q5-sandiego")), domain.regionIds)
    assertEquals(now, domain.lastCheckedAt)
    assertEquals(now, domain.lastUpdatedAt)
    assertEquals(now, domain.lastDiscoveredAt)
    assertEquals(now, domain.createdAt)
    assertEquals(now, domain.updatedAt)
  }

  @Test
  fun `toDomain should handle entity with empty regions`() {
    // Given
    val entity =
      FeedEntity(
        feedOnestopId = "f-9q8y-sfmta",
        name = "SF Muni",
        specType = FeedSpecType.GTFS,
        downloadUrl = "https://example.com/feed.zip",
        status = FeedStatus.ACTIVE,
      )

    // When
    val domain = mapper.toDomain(entity)

    // Then
    assertEquals(FeedId("f-9q8y-sfmta"), domain.feedId)
    assertTrue(domain.regionIds.isEmpty())
  }

  @Test
  fun `toDomain should handle entity with null optional fields`() {
    // Given
    val entity =
      FeedEntity(
        feedOnestopId = "f-9q8y-sfmta",
        name = "SF Muni",
        specType = FeedSpecType.GTFS,
        downloadUrl = "https://example.com/feed.zip",
        staticFeedUrl = null,
        realtimeFeedUrl = null,
        operatorName = null,
        currentVersionSha1 = null,
        status = FeedStatus.ACTIVE,
        lastCheckedAt = null,
        lastUpdatedAt = null,
        lastDiscoveredAt = null,
      )

    // When
    val domain = mapper.toDomain(entity)

    // Then
    assertEquals(FeedId("f-9q8y-sfmta"), domain.feedId)
    assertNull(domain.staticFeedUrl)
    assertNull(domain.realtimeFeedUrl)
    assertNull(domain.operatorName)
    assertNull(domain.currentVersionSha1)
    assertNull(domain.lastCheckedAt)
    assertNull(domain.lastUpdatedAt)
    assertNull(domain.lastDiscoveredAt)
  }

  @Test
  fun `toDomain should handle GTFS_RT spec type`() {
    // Given
    val entity =
      FeedEntity(
        feedOnestopId = "f-9q8y-sfmta~rt",
        name = "SF Muni Realtime",
        specType = FeedSpecType.GTFS_RT,
        downloadUrl = "https://example.com/realtime",
        status = FeedStatus.ACTIVE,
      )

    // When
    val domain = mapper.toDomain(entity)

    // Then
    assertEquals(FeedSpecType.GTFS_RT, domain.specType)
  }

  @Test
  fun `toEntity should convert domain model to entity with all fields`() {
    // Given
    val now = Instant.now()
    val domain =
      Feed(
        feedId = FeedId("f-9q8y-sfmta"),
        name = "SF Muni",
        operatorName = "San Francisco Municipal Transportation Agency",
        specType = FeedSpecType.GTFS,
        downloadUrl = "https://example.com/feed.zip",
        staticFeedUrl = "https://example.com/static.zip",
        realtimeFeedUrl = "https://example.com/realtime",
        currentVersionSha1 = "abc123def456",
        status = FeedStatus.ACTIVE,
        regionIds = setOf(RegionId("r-dpz8-sf"), RegionId("r-9q5-sandiego")),
        lastCheckedAt = now,
        lastUpdatedAt = now,
        lastDiscoveredAt = now,
        createdAt = now,
        updatedAt = now,
      )

    // When
    val entity = mapper.toEntity(domain)

    // Then
    assertEquals("f-9q8y-sfmta", entity.feedOnestopId)
    assertEquals("SF Muni", entity.name)
    assertEquals("San Francisco Municipal Transportation Agency", entity.operatorName)
    assertEquals(FeedSpecType.GTFS, entity.specType)
    assertEquals("https://example.com/feed.zip", entity.downloadUrl)
    assertEquals("https://example.com/static.zip", entity.staticFeedUrl)
    assertEquals("https://example.com/realtime", entity.realtimeFeedUrl)
    assertEquals("abc123def456", entity.currentVersionSha1)
    assertEquals(FeedStatus.ACTIVE, entity.status)
    assertEquals(now, entity.lastCheckedAt)
    assertEquals(now, entity.lastUpdatedAt)
    assertEquals(now, entity.lastDiscoveredAt)
    assertEquals(now, entity.createdAt)
    assertEquals(now, entity.updatedAt)
    // Note: regions collection is empty - managed separately by repository
    assertTrue(entity.regions.isEmpty())
  }

  @Test
  fun `toEntity should handle domain with null optional fields`() {
    // Given
    val domain =
      Feed(
        feedId = FeedId("f-9q8y-sfmta"),
        name = "SF Muni",
        operatorName = null,
        specType = FeedSpecType.GTFS,
        downloadUrl = "https://example.com/feed.zip",
        staticFeedUrl = null,
        realtimeFeedUrl = null,
        currentVersionSha1 = null,
        status = FeedStatus.ACTIVE,
        regionIds = emptySet(),
        lastCheckedAt = null,
        lastUpdatedAt = null,
        lastDiscoveredAt = null,
      )

    // When
    val entity = mapper.toEntity(domain)

    // Then
    assertEquals("f-9q8y-sfmta", entity.feedOnestopId)
    assertNull(entity.staticFeedUrl)
    assertNull(entity.realtimeFeedUrl)
    assertNull(entity.operatorName)
    assertNull(entity.currentVersionSha1)
    assertNull(entity.lastCheckedAt)
    assertNull(entity.lastUpdatedAt)
    assertNull(entity.lastDiscoveredAt)
  }

  @Test
  fun `toEntity should handle INACTIVE status`() {
    // Given
    val domain =
      Feed(
        feedId = FeedId("f-9q8y-sfmta"),
        name = "SF Muni",
        specType = FeedSpecType.GTFS,
        downloadUrl = "https://example.com/feed.zip",
        status = FeedStatus.INACTIVE,
      )

    // When
    val entity = mapper.toEntity(domain)

    // Then
    assertEquals(FeedStatus.INACTIVE, entity.status)
  }

  @Test
  fun `toEntity should handle ERROR status`() {
    // Given
    val domain =
      Feed(
        feedId = FeedId("f-9q8y-sfmta"),
        name = "SF Muni",
        specType = FeedSpecType.GTFS,
        downloadUrl = "https://example.com/feed.zip",
        status = FeedStatus.ERROR,
      )

    // When
    val entity = mapper.toEntity(domain)

    // Then
    assertEquals(FeedStatus.ERROR, entity.status)
  }

  @Test
  fun `bidirectional conversion should preserve all non-collection fields`() {
    // Given
    val now = Instant.now()
    val originalDomain =
      Feed(
        feedId = FeedId("f-9q8y-sfmta"),
        name = "SF Muni",
        operatorName = "SFMTA",
        specType = FeedSpecType.GTFS,
        downloadUrl = "https://example.com/feed.zip",
        staticFeedUrl = "https://example.com/static.zip",
        realtimeFeedUrl = "https://example.com/realtime",
        currentVersionSha1 = "abc123",
        status = FeedStatus.ACTIVE,
        regionIds = setOf(RegionId("r-dpz8-sf")),
        lastCheckedAt = now,
        lastUpdatedAt = now,
        lastDiscoveredAt = now,
        createdAt = now,
        updatedAt = now,
      )

    // When
    val entity = mapper.toEntity(originalDomain)
    // Simulate repository populating regions
    entity.regions.add(MetropolitanRegionEntity("r-dpz8-sf", "San Francisco Bay Area"))
    val convertedDomain = mapper.toDomain(entity)

    // Then
    assertEquals(originalDomain.feedId, convertedDomain.feedId)
    assertEquals(originalDomain.name, convertedDomain.name)
    assertEquals(originalDomain.operatorName, convertedDomain.operatorName)
    assertEquals(originalDomain.specType, convertedDomain.specType)
    assertEquals(originalDomain.downloadUrl, convertedDomain.downloadUrl)
    assertEquals(originalDomain.staticFeedUrl, convertedDomain.staticFeedUrl)
    assertEquals(originalDomain.realtimeFeedUrl, convertedDomain.realtimeFeedUrl)
    assertEquals(originalDomain.currentVersionSha1, convertedDomain.currentVersionSha1)
    assertEquals(originalDomain.status, convertedDomain.status)
    assertEquals(originalDomain.regionIds, convertedDomain.regionIds)
    assertEquals(originalDomain.lastCheckedAt, convertedDomain.lastCheckedAt)
    assertEquals(originalDomain.lastUpdatedAt, convertedDomain.lastUpdatedAt)
    assertEquals(originalDomain.lastDiscoveredAt, convertedDomain.lastDiscoveredAt)
    assertEquals(originalDomain.createdAt, convertedDomain.createdAt)
    assertEquals(originalDomain.updatedAt, convertedDomain.updatedAt)
  }
}
