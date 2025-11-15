package com.mobilispect.backend.feed.batch.discovery

import com.mobilispect.backend.feed.batch.discovery.RegionalFeedDiscoveryReader
import com.mobilispect.backend.feed.integration.TransitLandApiClient
import com.mobilispect.backend.feed.integration.TransitLandFeedSummary
import com.mobilispect.backend.feed.model.FeedSpecType
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.Instant

class RegionalFeedDiscoveryReaderTest {

    private lateinit var transitLandApiClient: TransitLandApiClient

    @BeforeEach
    fun setUp() {
        transitLandApiClient = mockk()
    }

    @Test
    fun `should read regional feeds from Transit land API`() {
        // Given
        val regionName = "San Francisco Bay Area"
        val feeds = listOf(
            createFeedSummary("f-9q9-caltrain"),
            createFeedSummary("f-9q9-bart"),
            createFeedSummary("f-9q9-muni")
        )

        coEvery {
            transitLandApiClient.discoverRegionalFeeds(regionName, FeedSpecType.GTFS)
        } returns feeds

        val reader = RegionalFeedDiscoveryReader(transitLandApiClient, regionName, "GTFS")

        // When
        val result1 = reader.read()
        val result2 = reader.read()
        val result3 = reader.read()
        val result4 = reader.read()

        // Then
        assertThat(result1).isNotNull
        assertThat(result1?.feed?.feedOnestopId).isEqualTo("f-9q9-caltrain")

        assertThat(result2).isNotNull
        assertThat(result2?.feed?.feedOnestopId).isEqualTo("f-9q9-bart")

        assertThat(result3).isNotNull
        assertThat(result3?.feed?.feedOnestopId).isEqualTo("f-9q9-muni")

        assertThat(result4).isNull() // End of data

        coVerify(exactly = 1) {
            transitLandApiClient.discoverRegionalFeeds(regionName, FeedSpecType.GTFS)
        }
    }

    @Test
    fun `should handle different region names`() {
        // Given
        val regionName = "New York Metropolitan Area"
        val feeds = listOf(
            createFeedSummary("f-dr5-nyct"),
            createFeedSummary("f-dr5-lirr")
        )

        coEvery {
            transitLandApiClient.discoverRegionalFeeds(regionName, FeedSpecType.GTFS)
        } returns feeds

        val reader = RegionalFeedDiscoveryReader(transitLandApiClient, regionName, "GTFS")

        // When
        val result1 = reader.read()
        val result2 = reader.read()
        val result3 = reader.read()

        // Then
        assertThat(result1?.feed?.feedOnestopId).isEqualTo("f-dr5-nyct")
        assertThat(result2?.feed?.feedOnestopId).isEqualTo("f-dr5-lirr")
        assertThat(result3).isNull()

        coVerify(exactly = 1) {
            transitLandApiClient.discoverRegionalFeeds(regionName, FeedSpecType.GTFS)
        }
    }

    @Test
    fun `should handle GTFS-RT spec type for regional feeds`() {
        // Given
        val regionName = "Los Angeles"
        val feeds = listOf(createFeedSummary("f-9q5-metro-rt"))

        coEvery {
            transitLandApiClient.discoverRegionalFeeds(regionName, FeedSpecType.GTFS_RT)
        } returns feeds

        val reader = RegionalFeedDiscoveryReader(transitLandApiClient, regionName, "GTFS_RT")

        // When
        val result = reader.read()

        // Then
        assertThat(result).isNotNull
        assertThat(result?.feed?.feedOnestopId).isEqualTo("f-9q5-metro-rt")

        coVerify(exactly = 1) {
            transitLandApiClient.discoverRegionalFeeds(regionName, FeedSpecType.GTFS_RT)
        }
    }

    @Test
    fun `should return null when no regional feeds are discovered`() {
        // Given
        val regionName = "Empty Region"
        coEvery {
            transitLandApiClient.discoverRegionalFeeds(regionName, FeedSpecType.GTFS)
        } returns emptyList()

        val reader = RegionalFeedDiscoveryReader(transitLandApiClient, regionName, "GTFS")

        // When
        val result = reader.read()

        // Then
        assertThat(result).isNull()
    }

    @Test
    fun `should initialize only once even with multiple reads`() {
        // Given
        val regionName = "Chicago"
        val feeds = listOf(createFeedSummary("f-dp3-cta"))

        coEvery {
            transitLandApiClient.discoverRegionalFeeds(regionName, FeedSpecType.GTFS)
        } returns feeds

        val reader = RegionalFeedDiscoveryReader(transitLandApiClient, regionName, "GTFS")

        // When
        reader.read()
        reader.read()
        reader.read()

        // Then - should only call API once
        coVerify(exactly = 1) {
            transitLandApiClient.discoverRegionalFeeds(any(), any())
        }
    }

    @Test
    fun `should propagate API exceptions during initialization`() {
        // Given
        val regionName = "Boston"
        val exception = RuntimeException("Transit.land API error for region")

        coEvery {
            transitLandApiClient.discoverRegionalFeeds(any(), any())
        } throws exception

        val reader = RegionalFeedDiscoveryReader(transitLandApiClient, regionName, "GTFS")

        // When/Then
        assertThrows<RuntimeException> {
            runBlocking {
                reader.read()
            }
        }
    }

    @Test
    fun `should handle regions with special characters in name`() {
        // Given
        val regionName = "Montréal, Québec"
        val feeds = listOf(createFeedSummary("f-stm-montreal"))

        coEvery {
            transitLandApiClient.discoverRegionalFeeds(regionName, FeedSpecType.GTFS)
        } returns feeds

        val reader = RegionalFeedDiscoveryReader(transitLandApiClient, regionName, "GTFS")

        // When
        val result = reader.read()

        // Then
        assertThat(result).isNotNull
        assertThat(result?.feed?.feedOnestopId).isEqualTo("f-stm-montreal")

        coVerify(exactly = 1) {
            transitLandApiClient.discoverRegionalFeeds(regionName, FeedSpecType.GTFS)
        }
    }

    private fun createFeedSummary(feedOnestopId: String): TransitLandFeedSummary {
        return TransitLandFeedSummary(
            feedOnestopId = feedOnestopId,
            name = "Test Feed $feedOnestopId",
            specType = FeedSpecType.GTFS,
            staticFeedUrl = "https://example.com/$feedOnestopId.zip",
            realtimeFeedUrl = null,
            operatorName = "Test Operator",
            latestVersionUrl = null,
            latestVersionSha1 = "abc123",
            latestVersionFetchedAt = Instant.now(),
            authorization = null
        )
    }
}