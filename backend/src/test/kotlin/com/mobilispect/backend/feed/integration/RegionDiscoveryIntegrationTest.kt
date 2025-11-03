package com.mobilispect.backend.feed.integration

import com.mobilispect.backend.feed.model.AuthType
import com.mobilispect.backend.feed.model.FeedSpecType
import com.mobilispect.backend.feed.model.FeedStatus
import com.mobilispect.backend.feed.model.MetropolitanRegion
import com.mobilispect.backend.feed.repository.FeedAuthenticationRepository
import com.mobilispect.backend.feed.repository.FeedRepository
import com.mobilispect.backend.feed.repository.MetropolitanRegionRepository
import com.mobilispect.backend.feed.service.FeedDiscoveryService
import io.micrometer.core.instrument.MeterRegistry
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.ContextConfiguration
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.reactive.function.client.WebClient
import java.time.Clock

/**
 * Integration test for the complete region discovery flow.
 * Tests the end-to-end process: Controller -> Service -> TransitLand API -> Database
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@ContextConfiguration(classes = [RegionDiscoveryIntegrationTestConfig::class])
class RegionDiscoveryIntegrationTest {

    @Autowired
    private lateinit var regionRepository: MetropolitanRegionRepository

    @Autowired
    private lateinit var feedRepository: FeedRepository

    @Autowired
    private lateinit var feedAuthenticationRepository: FeedAuthenticationRepository

    @Autowired
    private lateinit var meterRegistry: MeterRegistry

    @Autowired
    private lateinit var clock: Clock

    private lateinit var mockServer: MockWebServer
    private lateinit var transitLandClient: TransitLandApiClient
    private lateinit var discoveryService: FeedDiscoveryService

    private val testRegionId = "r-san-francisco-bay-area"
    private val testRegionName = "San Francisco Bay Area"

    @BeforeEach
    fun setUp() {
        mockServer = MockWebServer()
        mockServer.start()

        val baseUrl = mockServer.url("/api/").toString().trimEnd('/')
        transitLandClient = TransitLandApiClient(
            apiKey = "test-key",
            baseUrl = baseUrl,
            builder = WebClient.builder()
        )

        discoveryService = FeedDiscoveryService(
            regionRepository = regionRepository,
            feedRepository = feedRepository,
            feedAuthenticationRepository = feedAuthenticationRepository,
            transitLandApiClient = transitLandClient,
            meterRegistry = meterRegistry,
            clock = clock
        )

        // Set up test data
        val region = MetropolitanRegion(
            regionOnestopId = testRegionId,
            name = testRegionName,
            autoUpdateEnabled = true
        )
        regionRepository.save(region)
    }

    @AfterEach
    fun tearDown() {
        mockServer.shutdown()
    }

    @Test
    @Transactional
    fun `complete discovery flow creates feeds and authentication from TransitLand response`() = runBlocking {
        // Given - Mock TransitLand API response with 2 feeds, one with authentication
        val transitLandResponse = """
            {
              "feeds": [
                {
                  "onestop_id": "f-sf~bay~area~bart",
                  "name": "Bay Area Rapid Transit",
                  "spec": "gtfs",
                  "feed_versions": [
                    {
                      "sha1": "abc123def456",
                      "fetched_at": "2025-01-15T10:00:00Z",
                      "url": "https://example.com/feeds/bart.zip"
                    }
                  ],
                  "urls": {
                    "static_current": "https://example.com/feeds/bart.zip"
                  },
                  "operators": [
                    {
                      "onestop_id": "o-9q9-bart",
                      "name": "Bay Area Rapid Transit"
                    }
                  ]
                },
                {
                  "onestop_id": "f-sf~bay~area~sfmta",
                  "name": "San Francisco Municipal Transportation Agency",
                  "spec": "gtfs",
                  "feed_versions": [
                    {
                      "sha1": "xyz789ghi012",
                      "fetched_at": "2025-01-15T11:00:00Z",
                      "url": "https://example.com/feeds/sfmta.zip"
                    }
                  ],
                  "urls": {
                    "static_current": "https://example.com/feeds/sfmta.zip",
                    "realtime_trip_updates": "https://example.com/feeds/sfmta-rt.pb"
                  },
                  "authorization": {
                    "type": "api_key",
                    "param_name": "X-SFMTA-API-Key",
                    "info_url": "https://docs.sfmta.com/api/auth"
                  }
                }
              ]
            }
        """.trimIndent()

        mockServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody(transitLandResponse)
        )

        // When - Run discovery
        val result = discoveryService.discover(testRegionId, FeedSpecType.GTFS)

        // Then - Verify discovery result
        assertThat(result.regionOnestopId).isEqualTo(testRegionId)
        assertThat(result.feedsDiscovered).isEqualTo(2)
        assertThat(result.feedsCreated).isEqualTo(2)
        assertThat(result.feedsUpdated).isEqualTo(0)
        assertThat(result.errors).isEmpty()

        // Verify feeds were created in database
        val feeds = feedRepository.findAllByRegionRegionOnestopId(testRegionId)
        assertThat(feeds).hasSize(2)

        // Verify BART feed
        val bartFeed = feeds.find { it.feedOnestopId == "f-sf~bay~area~bart" }
        assertThat(bartFeed).isNotNull
        assertThat(bartFeed!!.name).isEqualTo("Bay Area Rapid Transit")
        assertThat(bartFeed.specType).isEqualTo(FeedSpecType.GTFS)
        assertThat(bartFeed.downloadUrl).isEqualTo("https://example.com/feeds/bart.zip")
        assertThat(bartFeed.currentVersionSha1).isEqualTo("abc123def456")
        assertThat(bartFeed.status).isEqualTo(FeedStatus.ACTIVE)
        assertThat(bartFeed.lastCheckedAt).isNotNull()
        assertThat(bartFeed.lastDiscoveredAt).isNotNull()

        // Verify SFMTA feed
        val sfmtaFeed = feeds.find { it.feedOnestopId == "f-sf~bay~area~sfmta" }
        assertThat(sfmtaFeed).isNotNull
        assertThat(sfmtaFeed!!.name).isEqualTo("San Francisco Municipal Transportation Agency")
        assertThat(sfmtaFeed.staticFeedUrl).isEqualTo("https://example.com/feeds/sfmta.zip")
        assertThat(sfmtaFeed.realtimeFeedUrl).isEqualTo("https://example.com/feeds/sfmta-rt.pb")

        // Verify authentication was created for SFMTA
        val sfmtaAuth = feedAuthenticationRepository.findById("f-sf~bay~area~sfmta")
        assertThat(sfmtaAuth).isPresent
        assertThat(sfmtaAuth.get().authType).isEqualTo(AuthType.API_KEY)
        assertThat(sfmtaAuth.get().headerName).isEqualTo("X-SFMTA-API-Key")
        assertThat(sfmtaAuth.get().isActive).isTrue()
        assertThat(sfmtaAuth.get().notes).contains("https://docs.sfmta.com/api/auth")

        // Verify no authentication for BART
        val bartAuth = feedAuthenticationRepository.findById("f-sf~bay~area~bart")
        assertThat(bartAuth).isEmpty

        // Verify metrics were recorded
        val discoveryTimer = meterRegistry.find("feed.discovery.duration")
            .tag("region", testRegionId)
            .tag("spec", "GTFS")
            .timer()
        assertThat(discoveryTimer).isNotNull
        assertThat(discoveryTimer!!.count()).isEqualTo(1)

        val createdCounter = meterRegistry.find("feed.discovery.feeds")
            .tag("region", testRegionId)
            .tag("outcome", "created")
            .counter()
        assertThat(createdCounter).isNotNull
        assertThat(createdCounter!!.count()).isEqualTo(2.0)
    }

    @Test
    @Transactional
    fun `discovery updates existing feed when new version available`() = runBlocking {
        // Given - Create existing feed
        val region = regionRepository.findById(testRegionId).get()
        val existingFeed = com.mobilispect.backend.feed.model.FeedEntity(
            feedOnestopId = "f-sf~bay~area~bart",
            region = region,
            name = "BART Old Name",
            specType = FeedSpecType.GTFS,
            downloadUrl = "https://old.example.com/bart.zip",
            currentVersionSha1 = "old-sha-123",
            lastCheckedAt = java.time.Instant.now(clock).minusSeconds(7200),
            lastUpdatedAt = java.time.Instant.now(clock).minusSeconds(7200),
            lastDiscoveredAt = java.time.Instant.now(clock).minusSeconds(7200),
            status = FeedStatus.ACTIVE
        )
        feedRepository.save(existingFeed)

        // Mock TransitLand API response with updated feed
        val transitLandResponse = """
            {
              "feeds": [
                {
                  "onestop_id": "f-sf~bay~area~bart",
                  "name": "Bay Area Rapid Transit",
                  "spec": "gtfs",
                  "feed_versions": [
                    {
                      "sha1": "new-sha-456",
                      "fetched_at": "2025-01-15T12:00:00Z",
                      "url": "https://example.com/feeds/bart-new.zip"
                    }
                  ],
                  "urls": {
                    "static_current": "https://example.com/feeds/bart-new.zip"
                  }
                }
              ]
            }
        """.trimIndent()

        mockServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody(transitLandResponse)
        )

        // When
        val result = discoveryService.discover(testRegionId, FeedSpecType.GTFS)

        // Then
        assertThat(result.feedsDiscovered).isEqualTo(1)
        assertThat(result.feedsCreated).isEqualTo(0)
        assertThat(result.feedsUpdated).isEqualTo(1)

        // Verify feed was updated
        val updatedFeed = feedRepository.findById("f-sf~bay~area~bart").get()
        assertThat(updatedFeed.name).isEqualTo("Bay Area Rapid Transit")
        assertThat(updatedFeed.currentVersionSha1).isEqualTo("new-sha-456")
        assertThat(updatedFeed.downloadUrl).isEqualTo("https://example.com/feeds/bart-new.zip")
        assertThat(updatedFeed.lastCheckedAt).isAfter(existingFeed.lastCheckedAt)
        assertThat(updatedFeed.lastUpdatedAt).isAfter(existingFeed.lastUpdatedAt)
    }

    @Test
    @Transactional
    fun `discovery handles TransitLand API errors gracefully`() = runBlocking {
        // Given - Mock server error
        mockServer.enqueue(
            MockResponse()
                .setResponseCode(500)
                .setHeader("Content-Type", "application/json")
                .setBody("""{"error": "Internal Server Error"}""")
        )

        // When
        val result = discoveryService.discover(testRegionId, FeedSpecType.GTFS)

        // Then
        assertThat(result.feedsDiscovered).isEqualTo(0)
        assertThat(result.feedsCreated).isEqualTo(0)
        assertThat(result.feedsUpdated).isEqualTo(0)
        assertThat(result.errors).hasSize(1)
        assertThat(result.errors.first()).contains("500")

        // Verify no feeds were created
        val feeds = feedRepository.findAllByRegionRegionOnestopId(testRegionId)
        assertThat(feeds).isEmpty()

        // Verify error metrics were recorded
        val errorCounter = meterRegistry.find("feed.discovery.feeds")
            .tag("region", testRegionId)
            .tag("outcome", "error")
            .counter()
        assertThat(errorCounter).isNotNull
        assertThat(errorCounter!!.count()).isEqualTo(1.0)
    }

    @Test
    @Transactional
    fun `discovery with GTFS-RT spec type queries correct feeds`() = runBlocking {
        // Given
        val transitLandResponse = """
            {
              "feeds": [
                {
                  "onestop_id": "f-sf~bay~area~bart~rt",
                  "name": "BART Realtime",
                  "spec": "gtfs-rt",
                  "feed_versions": [],
                  "urls": {
                    "realtime_trip_updates": "https://example.com/bart-rt.pb"
                  }
                }
              ]
            }
        """.trimIndent()

        mockServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody(transitLandResponse)
        )

        // When
        val result = discoveryService.discover(testRegionId, FeedSpecType.GTFS_RT)

        // Then
        assertThat(result.feedsCreated).isEqualTo(1)

        val feed = feedRepository.findById("f-sf~bay~area~bart~rt").get()
        assertThat(feed.specType).isEqualTo(FeedSpecType.GTFS_RT)
        assertThat(feed.realtimeFeedUrl).isEqualTo("https://example.com/bart-rt.pb")
    }
}
