package com.mobilispect.backend.schedule.transit_land

import com.mobilispect.backend.FeedDataSource
import com.mobilispect.backend.schedule.ScheduledFeed
import com.mobilispect.backend.schedule.transit_land.api.TransitLandCredentialsRepository
import org.slf4j.LoggerFactory

/**
 * A [FeedDataSource] that uses transit.land as its source.
 *
 * Queries the Transit.Land v2 REST API for GTFS feeds matching the specified region.
 * Uses the /feeds.json endpoint with search parameter for direct feed discovery.
 */
class TransitLandFeedDataSource(
    private val transitLandClient: TransitLandAPI,
    private val transitLandCredentialsRepository: TransitLandCredentialsRepository
) : FeedDataSource {
    private val logger = LoggerFactory.getLogger(TransitLandFeedDataSource::class.java)

    override fun feeds(region: String): Collection<Result<ScheduledFeed>> {
        val apiKey = transitLandCredentialsRepository.get()
            ?: return listOf(Result.failure(IllegalStateException("Missing API key")))

        // Transit.Land search works better with partial matches (first word or simple terms)
        // e.g., "francisco" matches better than "San Francisco"
        val searchTerm = region.split(" ").firstOrNull()?.lowercase() ?: region.lowercase()

        logger.debug("Querying feeds for region: {} (search term: {})", region, searchTerm)

        return transitLandClient.feeds(apiKey = apiKey, search = searchTerm, spec = "gtfs")
            .onSuccess { feeds -> logger.info("Found {} GTFS feeds for region '{}' (search: '{}')", feeds.size, region, searchTerm) }
            .onFailure { e -> logger.error("Failed to query feeds for region '{}': {}", region, e.message) }
            .map { feeds -> feeds.map { Result.success(it) } }
            .getOrElse { emptyList() }
    }
}
