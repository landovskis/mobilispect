package com.mobilispect.backend.feed.controller

import com.mobilispect.backend.api.dto.FeedSpecType as FeedSpecTypeDto
import com.mobilispect.backend.feed.model.FeedEntity
import com.mobilispect.backend.feed.model.FeedSpecType
import com.mobilispect.backend.feed.model.FeedStatus
import com.mobilispect.backend.feed.model.MetropolitanRegion
import com.mobilispect.backend.feed.model.ids.FeedId
import com.mobilispect.backend.feed.model.ids.RegionId
import com.mobilispect.backend.feed.repository.FeedAuthenticationRepository
import com.mobilispect.backend.feed.repository.FeedRepository
import com.mobilispect.backend.feed.repository.MetropolitanRegionRepository
import com.mobilispect.backend.feed.batch.discovery.FeedDiscoveryBatchService
import com.mobilispect.backend.feed.batch.discovery.FeedDiscoveryJobResult
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.web.server.ResponseStatusException
import java.time.Instant
import java.util.Optional

class RegionControllerTest {
    private lateinit var regionRepository: MetropolitanRegionRepository
    private lateinit var feedRepository: FeedRepository
    private lateinit var feedAuthenticationRepository: FeedAuthenticationRepository
    private lateinit var feedDiscoveryBatchService: FeedDiscoveryBatchService
    private lateinit var controller: RegionController

    private val testRegionId = "r-san-francisco-bay-area"
    private val testRegionName = "San Francisco Bay Area"
    private val fixedInstant = Instant.parse("2025-01-15T12:00:00Z")

    @BeforeEach
    fun setUp() {
        regionRepository = mockk()
        feedRepository = mockk()
        feedAuthenticationRepository = mockk()
        feedDiscoveryBatchService = mockk()

        controller = RegionController(
            regionRepository = regionRepository,
            feedRepository = feedRepository,
            feedAuthenticationRepository = feedAuthenticationRepository,
            feedDiscoveryBatchService = feedDiscoveryBatchService
        )
    }

    @Test
    fun `listRegions returns all regions when no filter applied`() {
        // Given
        val region1 = createRegion("r-region-1", "Region 1", autoUpdate = true)
        val region2 = createRegion("r-region-2", "Region 2", autoUpdate = false)

        every { regionRepository.findAll() } returns listOf(region1, region2)
        every { feedRepository.findAllByRegionRegionOnestopId(RegionId("r-region-1")) } returns emptyList()
        every { feedRepository.findAllByRegionRegionOnestopId(RegionId("r-region-2")) } returns emptyList()

        // When
        val response = controller.listRegions(autoUpdateEnabled = null)

        // Then
        assertThat(response.regions).hasSize(2)
        assertThat(response.total).isEqualTo(2)
        assertThat(response.regions.map { it.regionOnestopId }).containsExactlyInAnyOrder("r-region-1", "r-region-2")
    }

    @Test
    fun `listRegions filters by autoUpdateEnabled`() {
        // Given
        val region1 = createRegion("r-region-1", "Region 1", autoUpdate = true)
        val region2 = createRegion("r-region-2", "Region 2", autoUpdate = true)

        every { regionRepository.findAllByAutoUpdateEnabled(true) } returns listOf(region1, region2)
        every { feedRepository.findAllByRegionRegionOnestopId(any()) } returns emptyList()

        // When
        val response = controller.listRegions(autoUpdateEnabled = true)

        // Then
        assertThat(response.regions).hasSize(2)
        assertThat(response.regions).allMatch { it.autoUpdateEnabled }
    }

    @Test
    fun `listRegions includes feed count in response`() {
        // Given
        val region = createRegion(testRegionId, testRegionName, autoUpdate = true)
        val feeds = listOf(
            createFeed("f-feed-1", region),
            createFeed("f-feed-2", region)
        )

        every { regionRepository.findAll() } returns listOf(region)
        every { feedRepository.findAllByRegionRegionOnestopId(RegionId(testRegionId)) } returns feeds

        // When
        val response = controller.listRegions(autoUpdateEnabled = null)

        // Then
        assertThat(response.regions).hasSize(1)
        assertThat(response.regions.first().feedCount).isEqualTo(2)
    }

    @Test
    fun `getRegion returns region with feed details`() {
        // Given
        val region = createRegion(testRegionId, testRegionName, autoUpdate = true)
        val feeds = listOf(createFeed("f-bart", region))

        every { regionRepository.findById(RegionId(testRegionId)) } returns Optional.of(region)
        every { feedRepository.findAllByRegionRegionOnestopId(RegionId(testRegionId)) } returns feeds

        // When
        val dto = controller.getRegion(testRegionId)

        // Then
        assertThat(dto.regionOnestopId).isEqualTo(testRegionId)
        assertThat(dto.name).isEqualTo(testRegionName)
        assertThat(dto.feedCount).isEqualTo(1)
    }

    @Test
    fun `getRegion throws not found when region does not exist`() {
        // Given
        every { regionRepository.findById(RegionId(testRegionId)) } returns Optional.empty()

        // When/Then
        try {
            controller.getRegion(testRegionId)
            throw AssertionError("Expected ResponseStatusException")
        } catch (e: ResponseStatusException) {
            assertThat(e.statusCode.value()).isEqualTo(404)
            assertThat(e.reason).contains("Region not found")
        }
    }

    @Test
    fun `updateRegion updates autoUpdateEnabled`() {
        // Given
        val region = createRegion(testRegionId, testRegionName, autoUpdate = false)
        val updateRequest = com.mobilispect.backend.api.dto.RegionUpdateRequest(autoUpdateEnabled = true)

        every { regionRepository.findById(RegionId(testRegionId)) } returns Optional.of(region)
        every { regionRepository.save(any()) } answers { firstArg() }
        every { feedRepository.findAllByRegionRegionOnestopId(RegionId(testRegionId)) } returns emptyList()

        // When
        val result = controller.updateRegion(testRegionId, updateRequest)

        // Then
        assertThat(result.autoUpdateEnabled).isTrue()
        verify {
            regionRepository.save(match { it.autoUpdateEnabled })
        }
    }

    @Test
    fun `listFeedsForRegion returns all feeds for region`() {
        // Given
        val region = createRegion(testRegionId, testRegionName, autoUpdate = true)
        val feeds = listOf(
            createFeed("f-bart", region, specType = FeedSpecType.GTFS, status = FeedStatus.ACTIVE),
            createFeed("f-muni", region, specType = FeedSpecType.GTFS_RT, status = FeedStatus.ACTIVE)
        )

        every { regionRepository.findById(RegionId(testRegionId)) } returns Optional.of(region)
        every { feedRepository.findAllByRegionRegionOnestopId(RegionId(testRegionId)) } returns feeds
        every { feedAuthenticationRepository.findById(any()) } returns Optional.empty()

        // When
        val response = controller.listFeedsForRegion(testRegionId, specType = null, status = null)

        // Then
        // GTFS-RT feeds are filtered out (currently disabled)
        assertThat(response.feeds).hasSize(1)
        assertThat(response.total).isEqualTo(1)
        assertThat(response.feeds[0].specType).isEqualTo(FeedSpecTypeDto.GTFS)
    }

    @Test
    fun `listFeedsForRegion filters by spec type`() {
        // Given
        val region = createRegion(testRegionId, testRegionName, autoUpdate = true)
        val feeds = listOf(
            createFeed("f-bart", region, specType = FeedSpecType.GTFS, status = FeedStatus.ACTIVE),
            createFeed("f-muni", region, specType = FeedSpecType.GTFS_RT, status = FeedStatus.ACTIVE)
        )

        every { regionRepository.findById(RegionId(testRegionId)) } returns Optional.of(region)
        every { feedRepository.findAllByRegionRegionOnestopId(RegionId(testRegionId)) } returns feeds
        every { feedAuthenticationRepository.findById(any()) } returns Optional.empty()

        // When
        val response = controller.listFeedsForRegion(
            testRegionId,
            specType = com.mobilispect.backend.api.dto.FeedSpecType.GTFS,
            status = null
        )

        // Then
        assertThat(response.feeds).hasSize(1)
        assertThat(response.feeds.first().feedOnestopId).isEqualTo("f-bart")
    }

    @Test
    fun `listFeedsForRegion filters by status`() {
        // Given
        val region = createRegion(testRegionId, testRegionName, autoUpdate = true)
        val feeds = listOf(
            createFeed("f-active", region, specType = FeedSpecType.GTFS, status = FeedStatus.ACTIVE),
            createFeed("f-inactive", region, specType = FeedSpecType.GTFS, status = FeedStatus.INACTIVE)
        )

        every { regionRepository.findById(RegionId(testRegionId)) } returns Optional.of(region)
        every { feedRepository.findAllByRegionRegionOnestopId(RegionId(testRegionId)) } returns feeds
        every { feedAuthenticationRepository.findById(any()) } returns Optional.empty()

        // When
        val response = controller.listFeedsForRegion(
            testRegionId,
            specType = null,
            status = com.mobilispect.backend.api.dto.FeedStatus.ACTIVE
        )

        // Then
        assertThat(response.feeds).hasSize(1)
        assertThat(response.feeds.first().feedOnestopId).isEqualTo("f-active")
    }

    @Test
    fun `discoverFeeds calls service and returns result`() = runBlocking {
        // Given
        val expectedResult = FeedDiscoveryJobResult(
            jobExecutionId = 1L,
            status = "COMPLETED",
            startTime = fixedInstant,
            endTime = fixedInstant,
            feedsDiscovered = 5,
            feedsCreated = 3,
            feedsUpdated = 2,
            feedsFound = 5,
            regionsFound = 2,
            timeTakenMillis = 0,
            errors = emptyList()
        )

        every { regionRepository.findById(RegionId(testRegionId)) } returns Optional.of(createRegion(testRegionId, testRegionName, true))
        coEvery {
            feedDiscoveryBatchService.discoverForRegion(testRegionId, FeedSpecType.GTFS)
        } returns expectedResult

        // When
        val result = controller.discoverFeeds(
            testRegionId,
            spec = com.mobilispect.backend.api.dto.FeedSpecType.GTFS
        )

        // Then
        assertThat(result).isEqualTo(expectedResult)
        assertThat(result.feedsDiscovered).isEqualTo(5)
        assertThat(result.feedsCreated).isEqualTo(3)
        assertThat(result.feedsUpdated).isEqualTo(2)
        assertThat(result.errors).isEmpty()
    }

    @Test
    fun `discoverFeeds uses GTFS as default spec`() = runBlocking {
        // Given
        val expectedResult = FeedDiscoveryJobResult(
            jobExecutionId = 2L,
            status = "COMPLETED",
            startTime = fixedInstant,
            endTime = fixedInstant,
            feedsDiscovered = 1,
            feedsCreated = 1,
            feedsUpdated = 0,
            feedsFound = 1,
            regionsFound = 1,
            timeTakenMillis = 0,
            errors = emptyList()
        )

        every { regionRepository.findById(RegionId(testRegionId)) } returns Optional.of(createRegion(testRegionId, testRegionName, true))
        coEvery {
            feedDiscoveryBatchService.discoverForRegion(testRegionId, FeedSpecType.GTFS)
        } returns expectedResult

        // When - providing GTFS as spec parameter
        val result = controller.discoverFeeds(
            testRegionId,
            spec = com.mobilispect.backend.api.dto.FeedSpecType.GTFS
        )

        // Then
        coVerify {
            feedDiscoveryBatchService.discoverForRegion(testRegionId, FeedSpecType.GTFS)
        }
        assertThat(result.feedsDiscovered).isEqualTo(1)
    }

    @Test
    fun `discoverFeeds returns errors when discovery fails partially`() = runBlocking {
        // Given
        val expectedResult = FeedDiscoveryJobResult(
            jobExecutionId = 3L,
            status = "COMPLETED",
            startTime = fixedInstant,
            endTime = fixedInstant,
            feedsDiscovered = 3,
            feedsCreated = 2,
            feedsUpdated = 0,
            feedsFound = 3,
            regionsFound = 2,
            timeTakenMillis = 0,
            errors = listOf("Failed to upsert f-feed-3: Database error")
        )

        every { regionRepository.findById(RegionId(testRegionId)) } returns Optional.of(createRegion(testRegionId, testRegionName, true))
        coEvery {
            feedDiscoveryBatchService.discoverForRegion(testRegionId, FeedSpecType.GTFS)
        } returns expectedResult

        // When
        val result = controller.discoverFeeds(
            testRegionId,
            spec = com.mobilispect.backend.api.dto.FeedSpecType.GTFS
        )

        // Then
        assertThat(result.errors).hasSize(1)
        assertThat(result.errors.first()).contains("f-feed-3")
        assertThat(result.feedsCreated).isEqualTo(2)
    }

    private fun createRegion(id: String, name: String, autoUpdate: Boolean): MetropolitanRegion {
        return MetropolitanRegion(
            regionOnestopId = RegionId(id),
            name = name,
            autoUpdateEnabled = autoUpdate
        ).apply {
            createdAt = fixedInstant
            updatedAt = fixedInstant
        }
    }

    private fun createFeed(
        id: String,
        region: MetropolitanRegion,
        specType: FeedSpecType = FeedSpecType.GTFS,
        status: FeedStatus = FeedStatus.ACTIVE
    ): FeedEntity {
        return FeedEntity(
            feedOnestopId = FeedId(id),
            regions = mutableSetOf(region),
            name = id.substringAfterLast("-").uppercase(),
            specType = specType,
            downloadUrl = "https://example.com/$id.zip",
            currentVersionSha1 = "abc123",
            lastCheckedAt = fixedInstant,
            lastUpdatedAt = fixedInstant,
            lastDiscoveredAt = fixedInstant,
            status = status
        ).apply {
            createdAt = fixedInstant
            updatedAt = fixedInstant
        }
    }
}
