package com.mobilispect.backend.feed.repository

import com.mobilispect.backend.feed.domain.FeedImport
import com.mobilispect.backend.feed.model.FeedEntity
import com.mobilispect.backend.feed.model.FeedSpecType
import com.mobilispect.backend.feed.model.FeedStatus
import com.mobilispect.backend.feed.model.ImportStatus
import com.mobilispect.backend.feed.model.ImportTriggerType
import com.mobilispect.backend.feed.model.ids.ImportId
import com.mobilispect.backend.feed.model.ids.RegionId
import com.mobilispect.backend.region.domain.MetropolitanRegion
import java.time.Instant
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.testcontainers.service.connection.ServiceConnection
import org.springframework.transaction.annotation.Transactional
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers

@SpringBootTest
@Transactional
@Testcontainers
class MetropolitanRegionRepositoryTest {

  companion object {
    @Container
    @ServiceConnection
    @JvmStatic
    val postgres: PostgreSQLContainer<*> =
      PostgreSQLContainer("postgres:17-alpine")
        .withDatabaseName("mobilispect_test")
        .withUsername("test")
        .withPassword("test")
  }

  @Autowired private lateinit var regionRepository: MetropolitanRegionRepository

  @Autowired private lateinit var feedRepository: FeedRepository

  @Autowired private lateinit var feedImportRepository: FeedImportRepository

  private val fixedInstant = Instant.parse("2025-01-15T12:00:00Z")

  @BeforeEach
  fun setUp() {
    // Clean up before each test
    feedImportRepository.deleteAll()
    feedRepository.deleteAll()
    regionRepository.deleteAll()
  }

  @Test
  fun `findAllWithCompletedImports returns only regions with completed imports`() {
    // Given: 3 regions
    // Region 1: has feed with COMPLETED import
    val region1 = createAndSaveRegion("r-region-1", "Region 1", autoUpdate = true)
    val feed1 = createAndSaveFeed("f-feed-1", region1)
    createAndSaveFeedImport(feed1.feedId, ImportStatus.COMPLETED)

    // Region 2: has feed with only PENDING import
    val region2 = createAndSaveRegion("r-region-2", "Region 2", autoUpdate = true)
    val feed2 = createAndSaveFeed("f-feed-2", region2)
    createAndSaveFeedImport(feed2.feedId, ImportStatus.PENDING)

    // Region 3: has no feeds
    createAndSaveRegion("r-region-3", "Region 3", autoUpdate = true)

    // When
    val results = regionRepository.findAllWithCompletedImports()

    // Then
    assertThat(results).hasSize(1)
    assertThat(results[0].regionOnestopId.value).isEqualTo("r-region-1")
  }

  @Test
  fun `findAllWithCompletedImports handles region with multiple feeds`() {
    // Given: Region with 2 feeds, only 1 has completed import
    val region = createAndSaveRegion("r-multi-feed", "Multi Feed Region", autoUpdate = true)

    val feed1 = createAndSaveFeed("f-feed-1", region)
    createAndSaveFeedImport(feed1.feedId, ImportStatus.COMPLETED)

    val feed2 = createAndSaveFeed("f-feed-2", region)
    createAndSaveFeedImport(feed2.feedId, ImportStatus.PENDING)

    // When
    val results = regionRepository.findAllWithCompletedImports()

    // Then: Returns region once (DISTINCT works correctly)
    assertThat(results).hasSize(1)
    assertThat(results[0].regionOnestopId.value).isEqualTo("r-multi-feed")
  }

  @Test
  fun `findAllWithCompletedImports handles region with multiple completed imports on same feed`() {
    // Given: Region with 1 feed that has multiple COMPLETED imports
    val region = createAndSaveRegion("r-region-1", "Region 1", autoUpdate = true)
    val feed = createAndSaveFeed("f-feed-1", region)

    // Multiple completed imports for the same feed
    createAndSaveFeedImport(feed.feedId, ImportStatus.COMPLETED)
    createAndSaveFeedImport(feed.feedId, ImportStatus.COMPLETED)

    // When
    val results = regionRepository.findAllWithCompletedImports()

    // Then: Returns region once (DISTINCT works correctly)
    assertThat(results).hasSize(1)
    assertThat(results[0].regionOnestopId.value).isEqualTo("r-region-1")
  }

  @Test
  fun `findAllByAutoUpdateEnabledWithCompletedImports filters correctly`() {
    // Given: 2 regions with completed imports
    // Region 1: autoUpdateEnabled = true
    val region1 = createAndSaveRegion("r-region-1", "Region 1", autoUpdate = true)
    val feed1 = createAndSaveFeed("f-feed-1", region1)
    createAndSaveFeedImport(feed1.feedId, ImportStatus.COMPLETED)

    // Region 2: autoUpdateEnabled = false
    val region2 = createAndSaveRegion("r-region-2", "Region 2", autoUpdate = false)
    val feed2 = createAndSaveFeed("f-feed-2", region2)
    createAndSaveFeedImport(feed2.feedId, ImportStatus.COMPLETED)

    // When: Filter by autoUpdateEnabled = true
    val results = regionRepository.findAllByAutoUpdateEnabledWithCompletedImports(true)

    // Then
    assertThat(results).hasSize(1)
    assertThat(results[0].regionOnestopId.value).isEqualTo("r-region-1")
    assertThat(results[0].autoUpdateEnabled).isTrue()
  }

  @Test
  fun `findAllByAutoUpdateEnabledWithCompletedImports returns regions with autoUpdate false`() {
    // Given: 2 regions with completed imports
    val region1 = createAndSaveRegion("r-region-1", "Region 1", autoUpdate = true)
    val feed1 = createAndSaveFeed("f-feed-1", region1)
    createAndSaveFeedImport(feed1.feedId, ImportStatus.COMPLETED)

    val region2 = createAndSaveRegion("r-region-2", "Region 2", autoUpdate = false)
    val feed2 = createAndSaveFeed("f-feed-2", region2)
    createAndSaveFeedImport(feed2.feedId, ImportStatus.COMPLETED)

    // When: Filter by autoUpdateEnabled = false
    val results = regionRepository.findAllByAutoUpdateEnabledWithCompletedImports(false)

    // Then
    assertThat(results).hasSize(1)
    assertThat(results[0].regionOnestopId.value).isEqualTo("r-region-2")
    assertThat(results[0].autoUpdateEnabled).isFalse()
  }

  @Test
  fun `findAllWithCompletedImports excludes regions with only failed imports`() {
    // Given: Region with feed that has FAILED import
    val region1 = createAndSaveRegion("r-region-1", "Region 1", autoUpdate = true)
    val feed1 = createAndSaveFeed("f-feed-1", region1)
    createAndSaveFeedImport(feed1.feedId, ImportStatus.FAILED)

    // Region with feed that has CANCELLED import
    val region2 = createAndSaveRegion("r-region-2", "Region 2", autoUpdate = true)
    val feed2 = createAndSaveFeed("f-feed-2", region2)
    createAndSaveFeedImport(feed2.feedId, ImportStatus.CANCELLED)

    // When
    val results = regionRepository.findAllWithCompletedImports()

    // Then
    assertThat(results).isEmpty()
  }

  @Test
  fun `findAllWithCompletedImports handles many-to-many feed-region relationships`() {
    // Given: Feed associated with multiple regions, has COMPLETED import
    val region1 = createAndSaveRegion("r-region-1", "Region 1", autoUpdate = true)
    val region2 = createAndSaveRegion("r-region-2", "Region 2", autoUpdate = true)

    // Create a feed associated with both regions
    val feed =
      FeedEntity(
        feedId = "f-shared-feed",
        regions = mutableSetOf(region1, region2),
        name = "Shared Feed",
        downloadUrl = "https://example.com/feed.zip",
        specType = FeedSpecType.GTFS,
        status = FeedStatus.ACTIVE,
        createdAt = fixedInstant,
        updatedAt = fixedInstant,
      )
    feedRepository.save(feed)

    createAndSaveFeedImport(feed.feedId, ImportStatus.COMPLETED)

    // When
    val results = regionRepository.findAllWithCompletedImports()

    // Then: Both regions returned
    assertThat(results).hasSize(2)
    assertThat(results.map { it.regionOnestopId.value })
      .containsExactlyInAnyOrder("r-region-1", "r-region-2")
  }

  @Test
  fun `findAllWithCompletedImports returns empty list when no regions exist`() {
    // Given: No regions in database

    // When
    val results = regionRepository.findAllWithCompletedImports()

    // Then
    assertThat(results).isEmpty()
  }

  @Test
  fun `findAllWithCompletedImports returns empty list when no feeds have completed imports`() {
    // Given: Regions with feeds but no completed imports
    val region1 = createAndSaveRegion("r-region-1", "Region 1", autoUpdate = true)
    val feed1 = createAndSaveFeed("f-feed-1", region1)
    createAndSaveFeedImport(feed1.feedId, ImportStatus.RUNNING)

    val region2 = createAndSaveRegion("r-region-2", "Region 2", autoUpdate = true)
    val feed2 = createAndSaveFeed("f-feed-2", region2)
    createAndSaveFeedImport(feed2.feedId, ImportStatus.PENDING)

    // When
    val results = regionRepository.findAllWithCompletedImports()

    // Then
    assertThat(results).isEmpty()
  }

  // Helper methods for creating test data

  private fun createAndSaveRegion(
    regionId: String,
    name: String,
    autoUpdate: Boolean = true,
  ): MetropolitanRegion {
    val region =
      MetropolitanRegion(
          regionOnestopId = RegionId(regionId),
          name = name,
          autoUpdateEnabled = autoUpdate,
        )
        .apply {
          createdAt = fixedInstant
          updatedAt = fixedInstant
        }
    return regionRepository.save(region)
  }

  private fun createAndSaveFeed(
    feedId: String,
    region: MetropolitanRegion,
    specType: FeedSpecType = FeedSpecType.GTFS,
    status: FeedStatus = FeedStatus.ACTIVE,
  ): FeedEntity {
    val feed =
      FeedEntity(
        feedId = feedId,
        regions = mutableSetOf(region),
        name = feedId.substringAfterLast("-").uppercase(),
        downloadUrl = "https://example.com/$feedId.zip",
        specType = specType,
        status = status,
        createdAt = fixedInstant,
        updatedAt = fixedInstant,
      )
    return feedRepository.save(feed)
  }

  private fun createAndSaveFeedImport(
    feedId: String,
    status: ImportStatus,
    triggerType: ImportTriggerType = ImportTriggerType.MANUAL,
  ): FeedImport {
    val feedImport =
      FeedImport(
        id = ImportId.random(),
        feedId = feedId,
        status = status,
        triggerType = triggerType,
        startedAt = fixedInstant,
        completedAt = if (status == ImportStatus.COMPLETED) fixedInstant else null,
        createdAt = fixedInstant,
        updatedAt = fixedInstant,
      )
    return feedImportRepository.save(feedImport)
  }
}
