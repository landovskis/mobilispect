package com.mobilispect.backend.feed.service

import com.mobilispect.backend.feed.integration.TransitLandApiClient
import com.mobilispect.backend.feed.integration.TransitLandApiException
import com.mobilispect.backend.feed.integration.TransitLandAuthorizationSummary
import com.mobilispect.backend.feed.integration.TransitLandFeedSummary
import com.mobilispect.backend.feed.model.AuthType
import com.mobilispect.backend.feed.model.FeedAuthentication
import com.mobilispect.backend.feed.model.FeedEntity
import com.mobilispect.backend.feed.model.FeedSpecType
import com.mobilispect.backend.feed.model.FeedStatus
import com.mobilispect.backend.feed.model.MetropolitanRegion
import com.mobilispect.backend.feed.repository.FeedAuthenticationRepository
import com.mobilispect.backend.feed.repository.FeedRepository
import com.mobilispect.backend.feed.repository.MetropolitanRegionRepository
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.util.Optional

class FeedDiscoveryServiceTest {
    private lateinit var regionRepository: MetropolitanRegionRepository
    private lateinit var feedRepository: FeedRepository
    private lateinit var feedAuthenticationRepository: FeedAuthenticationRepository
    private lateinit var transitLandApiClient: TransitLandApiClient
    private lateinit var meterRegistry: MeterRegistry
    private lateinit var clock: Clock
    private lateinit var service: FeedDiscoveryService

    private val fixedInstant = Instant.parse("2025-01-15T12:00:00Z")
    private val testRegionId = "r-san-francisco-bay-area"
    private val testRegionName = "San Francisco Bay Area"

    @BeforeEach
    fun setUp() {
        regionRepository = mockk()
        feedRepository = mockk()
        feedAuthenticationRepository = mockk()
        transitLandApiClient = mockk()
        meterRegistry = SimpleMeterRegistry()
        clock = Clock.fixed(fixedInstant, ZoneId.of("UTC"))

        service = FeedDiscoveryService(
            regionRepository = regionRepository,
            feedRepository = feedRepository,
            feedAuthenticationRepository = feedAuthenticationRepository,
            transitLandApiClient = transitLandApiClient,
            meterRegistry = meterRegistry,
            clock = clock
        )
    }

    @Test
    fun `discover creates new feed when not exists`() = runBlocking {
        // Given
        val region = createTestRegion()
        val feedSummary = createFeedSummary(
            onestopId = "f-sf~bay~area~bart",
            name = "BART",
            staticUrl = "https://example.com/bart.zip",
            sha1 = "abc123"
        )

        every { regionRepository.findById(testRegionId) } returns Optional.of(region)
        every { regionRepository.getReferenceById(testRegionId) } returns region
        coEvery { transitLandApiClient.discoverRegionalFeeds(testRegionName, FeedSpecType.GTFS) } returns listOf(feedSummary)
        every { feedRepository.findById(feedSummary.feedOnestopId) } returns Optional.empty()
        every { feedRepository.save(any()) } answers { firstArg() }

        // When
        val result = service.discover(testRegionId, FeedSpecType.GTFS)

        // Then
        assertThat(result.regionOnestopId).isEqualTo(testRegionId)
        assertThat(result.feedsDiscovered).isEqualTo(1)
        assertThat(result.feedsCreated).isEqualTo(1)
        assertThat(result.feedsUpdated).isEqualTo(0)
        assertThat(result.errors).isEmpty()

        verify {
            feedRepository.save(match { feed ->
                feed.feedOnestopId == feedSummary.feedOnestopId &&
                    feed.name == "BART" &&
                    feed.downloadUrl == "https://example.com/bart.zip" &&
                    feed.currentVersionSha1 == "abc123" &&
                    feed.status == FeedStatus.ACTIVE &&
                    feed.lastCheckedAt == fixedInstant &&
                    feed.lastDiscoveredAt == fixedInstant
            })
        }
    }

    @Test
    fun `discover updates existing feed with new SHA1`() = runBlocking {
        // Given
        val region = createTestRegion()
        val existingFeed = createExistingFeed(sha1 = "old-sha")
        val feedSummary = createFeedSummary(
            onestopId = existingFeed.feedOnestopId,
            name = "BART Updated",
            staticUrl = "https://example.com/bart-new.zip",
            sha1 = "new-sha"
        )

        every { regionRepository.findById(testRegionId) } returns Optional.of(region)
        every { regionRepository.getReferenceById(testRegionId) } returns region
        coEvery { transitLandApiClient.discoverRegionalFeeds(testRegionName, FeedSpecType.GTFS) } returns listOf(feedSummary)
        every { feedRepository.findById(existingFeed.feedOnestopId) } returns Optional.of(existingFeed)
        every { feedRepository.save(any()) } answers { firstArg() }

        // When
        val result = service.discover(testRegionId, FeedSpecType.GTFS)

        // Then
        assertThat(result.feedsDiscovered).isEqualTo(1)
        assertThat(result.feedsCreated).isEqualTo(0)
        assertThat(result.feedsUpdated).isEqualTo(1)
        assertThat(result.errors).isEmpty()

        verify {
            feedRepository.save(match { feed ->
                feed.currentVersionSha1 == "new-sha" &&
                    feed.lastUpdatedAt == fixedInstant &&
                    feed.lastCheckedAt == fixedInstant
            })
        }
    }

    @Test
    fun `discover handles feed with authentication`() = runBlocking {
        // Given
        val region = createTestRegion()
        val feedSummary = createFeedSummary(
            onestopId = "f-sf~bay~area~sfmta",
            name = "SFMTA",
            authorization = TransitLandAuthorizationSummary(
                type = "api_key",
                parameterName = "X-API-Key",
                infoUrl = "https://docs.sfmta.com/auth"
            )
        )

        every { regionRepository.findById(testRegionId) } returns Optional.of(region)
        every { regionRepository.getReferenceById(testRegionId) } returns region
        coEvery { transitLandApiClient.discoverRegionalFeeds(testRegionName, FeedSpecType.GTFS) } returns listOf(feedSummary)
        every { feedRepository.findById(any()) } returns Optional.empty()
        every { feedRepository.save(any()) } answers { firstArg() }
        every { feedAuthenticationRepository.findById(any()) } returns Optional.empty()
        every { feedAuthenticationRepository.save(any()) } answers { firstArg() }

        // When
        val result = service.discover(testRegionId, FeedSpecType.GTFS)

        // Then
        assertThat(result.feedsCreated).isEqualTo(1)
        verify {
            feedAuthenticationRepository.save(match { auth ->
                auth.authType == AuthType.API_KEY &&
                    auth.headerName == "X-API-Key" &&
                    auth.isActive == true &&
                    auth.notes?.contains("https://docs.sfmta.com/auth") == true
            })
        }
    }

    @Test
    fun `discover returns error when region not found`() = runBlocking {
        // Given
        every { regionRepository.findById(testRegionId) } returns Optional.empty()

        // When/Then
        assertThatThrownBy {
            runBlocking { service.discover(testRegionId, FeedSpecType.GTFS) }
        }
            .isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("Region not found")
    }

    @Test
    fun `discover handles TransitLand API error gracefully`() = runBlocking {
        // Given
        val region = createTestRegion()
        every { regionRepository.findById(testRegionId) } returns Optional.of(region)
        coEvery { transitLandApiClient.discoverRegionalFeeds(testRegionName, FeedSpecType.GTFS) } throws
            TransitLandApiException("API Error", org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR)

        // When
        val result = service.discover(testRegionId, FeedSpecType.GTFS)

        // Then
        assertThat(result.feedsDiscovered).isEqualTo(0)
        assertThat(result.feedsCreated).isEqualTo(0)
        assertThat(result.feedsUpdated).isEqualTo(0)
        assertThat(result.errors).hasSize(1)
        assertThat(result.errors.first()).contains("API Error")
    }

    @Test
    fun `discover processes multiple feeds and handles partial failures`() = runBlocking {
        // Given
        val region = createTestRegion()
        val feed1 = createFeedSummary(onestopId = "f-feed-1", name = "Feed 1")
        val feed2 = createFeedSummary(onestopId = "f-feed-2", name = "Feed 2")

        every { regionRepository.findById(testRegionId) } returns Optional.of(region)
        every { regionRepository.getReferenceById(testRegionId) } returns region
        coEvery { transitLandApiClient.discoverRegionalFeeds(testRegionName, FeedSpecType.GTFS) } returns listOf(feed1, feed2)
        every { feedRepository.findById("f-feed-1") } returns Optional.empty()
        every { feedRepository.findById("f-feed-2") } returns Optional.empty()
        every { feedRepository.save(match { it.feedOnestopId == "f-feed-1" }) } answers { firstArg() }
        every { feedRepository.save(match { it.feedOnestopId == "f-feed-2" }) } throws RuntimeException("Database error")

        // When
        val result = service.discover(testRegionId, FeedSpecType.GTFS)

        // Then
        assertThat(result.feedsDiscovered).isEqualTo(2)
        assertThat(result.feedsCreated).isEqualTo(1)
        assertThat(result.feedsUpdated).isEqualTo(0)
        assertThat(result.errors).hasSize(1)
        assertThat(result.errors.first()).contains("f-feed-2")
        assertThat(result.errors.first()).contains("Database error")
    }

    @Test
    fun `discover infers feed name from onestop ID when name is blank`() = runBlocking {
        // Given
        val region = createTestRegion()
        val feedSummary = createFeedSummary(
            onestopId = "f-test-golden-gate-transit",
            name = "" // blank name
        )

        every { regionRepository.findById(testRegionId) } returns Optional.of(region)
        every { regionRepository.getReferenceById(testRegionId) } returns region
        coEvery { transitLandApiClient.discoverRegionalFeeds(testRegionName, FeedSpecType.GTFS) } returns listOf(feedSummary)
        every { feedRepository.findById(any()) } returns Optional.empty()
        every { feedRepository.save(any()) } answers { firstArg() }

        // When
        service.discover(testRegionId, FeedSpecType.GTFS)

        // Then
        verify {
            feedRepository.save(match { feed ->
                feed.name == "Golden Gate Transit" // Inferred from onestop ID (parts after f-test-)
            })
        }
    }

    @Test
    fun `discover records metrics for success`() = runBlocking {
        // Given
        val region = createTestRegion()
        val feedSummary = createFeedSummary(onestopId = "f-test", name = "Test")

        every { regionRepository.findById(testRegionId) } returns Optional.of(region)
        every { regionRepository.getReferenceById(testRegionId) } returns region
        coEvery { transitLandApiClient.discoverRegionalFeeds(testRegionName, FeedSpecType.GTFS) } returns listOf(feedSummary)
        every { feedRepository.findById(any()) } returns Optional.empty()
        every { feedRepository.save(any()) } answers { firstArg() }

        // When
        service.discover(testRegionId, FeedSpecType.GTFS)

        // Then
        val timer = meterRegistry.find("feed.discovery.duration")
            .tag("region", testRegionId)
            .tag("spec", "GTFS")
            .tag("status", "success")
            .timer()
        assertThat(timer).isNotNull
        assertThat(timer!!.count()).isEqualTo(1)

        val counter = meterRegistry.find("feed.discovery.feeds")
            .tag("region", testRegionId)
            .tag("outcome", "created")
            .counter()
        assertThat(counter).isNotNull
        assertThat(counter!!.count()).isEqualTo(1.0)
    }

    private fun createTestRegion(): MetropolitanRegion {
        return MetropolitanRegion(
            regionOnestopId = testRegionId,
            name = testRegionName,
            autoUpdateEnabled = true
        )
    }

    private fun createFeedSummary(
        onestopId: String,
        name: String,
        staticUrl: String? = null,
        sha1: String? = null,
        authorization: TransitLandAuthorizationSummary? = null
    ): TransitLandFeedSummary {
        return TransitLandFeedSummary(
            feedOnestopId = onestopId,
            name = name,
            specType = FeedSpecType.GTFS,
            staticFeedUrl = staticUrl,
            realtimeFeedUrl = null,
            latestVersionUrl = null,
            latestVersionSha1 = sha1,
            latestVersionFetchedAt = if (sha1 != null) fixedInstant else null,
            operatorName = null,
            authorization = authorization
        )
    }

    private fun createExistingFeed(sha1: String): FeedEntity {
        val region = createTestRegion()
        return FeedEntity(
            feedOnestopId = "f-sf~bay~area~bart",
            region = region,
            name = "BART",
            specType = FeedSpecType.GTFS,
            downloadUrl = "https://example.com/bart.zip",
            currentVersionSha1 = sha1,
            lastCheckedAt = fixedInstant.minusSeconds(3600),
            lastUpdatedAt = fixedInstant.minusSeconds(7200),
            lastDiscoveredAt = fixedInstant.minusSeconds(3600),
            status = FeedStatus.ACTIVE
        )
    }
}
