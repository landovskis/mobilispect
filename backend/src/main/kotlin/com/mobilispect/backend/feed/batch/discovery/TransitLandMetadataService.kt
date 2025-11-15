package com.mobilispect.backend.feed.batch.discovery

import com.mobilispect.backend.TransitLandFeedResponse
import com.mobilispect.backend.feed.model.FeedId
import com.mobilispect.backend.feed.model.FeedSpecType
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.MediaType
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
import java.time.LocalDate

/**
 * Service for fetching feed metadata from Transit.land API.
 *
 * This service provides a centralized, reusable way to fetch feed metadata
 * that can be shared across multiple batch components without duplicating
 * API interaction logic.
 */
@Service
class TransitLandMetadataService(
    private val webClient: WebClient.Builder,
    @Value("\${app.transit-land.api-key:}")
    private val defaultApiKeyString: String
) {

    private val logger = LoggerFactory.getLogger(TransitLandMetadataService::class.java)

    // Minimal validation for Transit.land feed onestop IDs
    // Transit.land feeds have highly variable formats including:
    // - Standard ASCII: f-9q9-bart, f-dr5r-nyct
    // - Multiple tildes: f-dr5r-path~nj~us
    // - Non-ASCII characters: f-xn4n-島田市 (Japanese)
    // - Accented characters: f-u3z-klaipėdoskeleivinistransportas (Lithuanian)
    // We only check that it starts with 'f-' and is not empty
    private val feedOnestopIdPattern = Regex("^f-.+$")

    private val defaultApiKey: TransitLandAPIKey? by lazy {
        TransitLandAPIKey.fromNullable(defaultApiKeyString)
    }

    /**
     * Fetches metadata for a single feed from Transit.land API.
     *
     * @param feedId The feed's onestop ID
     * @param apiKey Optional API key (uses default if not provided)
     * @return FeedMetadata if found, null otherwise
     */
    fun fetchFeedMetadata(feedId: FeedId, apiKey: TransitLandAPIKey? = null): FeedMetadata? {
        // Validate feed ID format first
        if (!isValidFeedOnestopId(feedId.value)) {
            logger.warn("Skipping metadata fetch for invalid feed onestop ID: {}", feedId.value)
            return null
        }

        val key = apiKey ?: defaultApiKey
            ?: throw IllegalStateException(
                "Transit.land API key not configured. Set app.transit-land.api-key property"
            )

        val client = webClient.baseUrl("https://transit.land/api/v2/rest")
            .defaultHeader("apikey", key.value)
            .build()

        return try {
            val uri = "/feeds.json?onestop_id=${feedId.value}&include_alerts=false"

            logger.debug("Fetching feed metadata from Transit.land: feedId={}", feedId.value)

            val response = client.get()
                .uri(uri)
                .accept(MediaType.APPLICATION_JSON)
                .retrieve()
                .bodyToMono(TransitLandFeedResponse::class.java)
                .block()

            if (response == null) {
                logger.warn("Received null response for feed: {}", feedId.value)
                return null
            }

            val feed = response.feeds.firstOrNull()
            if (feed == null) {
                logger.warn("No feed data found for feed ID: {}", feedId.value)
                return null
            }

            // Extract latest version information
            val latestVersion = feed.feed_versions.firstOrNull()
            if (latestVersion == null) {
                logger.warn("No version information available for feed: {}", feedId.value)
                return null
            }

            // Determine spec type
            val specType = when (feed.spec.lowercase()) {
                "gtfs" -> FeedSpecType.GTFS
                "gtfs-rt" -> FeedSpecType.GTFS_RT
                else -> {
                    logger.warn("Unknown spec type '{}' for feed: {}, defaulting to GTFS",
                        feed.spec, feedId.value)
                    FeedSpecType.GTFS
                }
            }

            // Create metadata object
            val metadata = FeedMetadata(
                feedOnestopId = FeedId.from(feed.onestop_id) ?: feedId,
                name = feed.name ?: "Unknown",
                downloadUrl = latestVersion.url,
                specType = specType,
                versionSha1 = latestVersion.sha1,
                earliestCalendarDate = LocalDate.parse(latestVersion.earliest_calendar_date),
                latestCalendarDate = LocalDate.parse(latestVersion.latest_calendar_date),
                staticFeedUrl = feed.urls?.static_current,
                realtimeFeedUrl = feed.urls?.realtime_trip_updates,
                authorizationType = feed.authorization?.type,
                authorizationInfoUrl = feed.authorization?.info_url
            )

            logger.debug(
                "Successfully fetched metadata for feed: {} (version: {})",
                feedId.value,
                latestVersion.sha1
            )

            metadata

        } catch (e: Exception) {
            logger.error("Failed to fetch metadata for feed: {}", feedId.value, e)
            null
        }
    }

    /**
     * Fetches metadata for multiple feeds in parallel.
     *
     * @param feedIds Collection of feed onestop IDs
     * @param apiKey Optional API key
     * @return Map of feed ID to metadata (only includes feeds where metadata was found)
     */
    fun fetchFeedMetadataBatch(
        feedIds: Collection<FeedId>,
        apiKey: TransitLandAPIKey? = null
    ): Map<FeedId, FeedMetadata> {
        val results = mutableMapOf<FeedId, FeedMetadata>()

        // Filter out invalid feed IDs first
        val validFeedIds = feedIds.filter { isValidFeedOnestopId(it.value) }
        val invalidCount = feedIds.size - validFeedIds.size

        if (invalidCount > 0) {
            logger.warn("Filtered out {} invalid feed onestop IDs from batch", invalidCount)
        }

        for (feedId in validFeedIds) {
            val metadata = fetchFeedMetadata(feedId, apiKey)
            if (metadata != null) {
                results[feedId] = metadata
            }
        }

        return results
    }

    /**
     * Validates whether a feed ID has the minimal required format.
     *
     * Minimal validation:
     * - Must start with 'f-'
     * - Must have at least one character after 'f-'
     *
     * Examples of valid IDs:
     * - Standard: f-9q9-bart, f-dr5r-nyct
     * - With tildes: f-dr5r-path~nj~us
     * - Non-ASCII: f-xn4n-島田市
     * - Accented: f-u3z-klaipėdoskeleivinistransportas
     *
     * Examples of invalid IDs: bart, f-, F-9q9-bart (uppercase F)
     *
     * @param feedId The feed onestop ID to validate
     * @return true if the ID is valid, false otherwise
     */
    private fun isValidFeedOnestopId(feedId: String): Boolean {
        return feedOnestopIdPattern.matches(feedId)
    }
}
