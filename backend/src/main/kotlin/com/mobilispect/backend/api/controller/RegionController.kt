package com.mobilispect.backend.api.controller

import com.mobilispect.backend.FeedDataSource
import com.mobilispect.backend.api.dto.*
import com.mobilispect.backend.schedule.transit_land.TransitLandAPI
import com.mobilispect.backend.schedule.transit_land.api.TransitLandCredentialsRepository
import org.slf4j.LoggerFactory
import org.springframework.web.bind.annotation.*
import java.time.Instant
import java.util.*

/**
 * Region Management REST Controller
 *
 * Provides REST API endpoints for managing metropolitan regions and their feeds.
 */
@RestController
@RequestMapping("/api/feed-management/regions")
class RegionController(
    private val feedDataSource: FeedDataSource,
    private val transitLandAPI: TransitLandAPI,
    private val credentialsRepository: TransitLandCredentialsRepository
) {
    private val logger = LoggerFactory.getLogger(RegionController::class.java)

    /**
     * List all metropolitan regions
     *
     * GET /api/feed-management/regions
     */
    @GetMapping
    fun listRegions(
        @RequestParam(required = false) autoUpdateEnabled: Boolean?
    ): RegionsResponse {
        // Mock data with Canadian cities prioritized
        val allRegions = listOf(
            createRegion("r-toronto-on", "Toronto, Ontario", true, 18),
            createRegion("r-vancouver-bc", "Vancouver, British Columbia", true, 12),
            createRegion("r-montreal-qc", "Montreal, Quebec", true, 14),
            createRegion("r-calgary-ab", "Calgary, Alberta", true, 8),
            createRegion("r-ottawa-on", "Ottawa, Ontario", true, 6),
            createRegion("r-winnipeg-mb", "Winnipeg, Manitoba", true, 5),
            createRegion("r-edmonton-ab", "Edmonton, Alberta", true, 7),
            createRegion("r-sf-bay-area", "San Francisco Bay Area", true, 15),
            createRegion("r-nyc-metro", "New York City Metro", true, 8),
            createRegion("r-la-metro", "Los Angeles Metro", false, 12)
        )

        val filteredRegions = if (autoUpdateEnabled != null) {
            allRegions.filter { it.autoUpdateEnabled == autoUpdateEnabled }
        } else {
            allRegions
        }

        return RegionsResponse(
            regions = filteredRegions,
            total = filteredRegions.size
        )
    }

    /**
     * Get feeds for a specific region
     *
     * GET /api/feed-management/regions/{regionOnestopId}/feeds
     */
    @GetMapping("/{regionOnestopId}/feeds")
    fun listFeedsForRegion(
        @PathVariable regionOnestopId: String,
        @RequestParam(required = false) specType: FeedSpecType?,
        @RequestParam(required = false) status: FeedStatus?
    ): FeedsResponse {
        // Extract region name from onestop ID (e.g., "r-montreal-qc" -> "Montreal")
        val regionName = extractRegionName(regionOnestopId)

        logger.info("Fetching feeds for region: $regionName (onestopId: $regionOnestopId)")

        // Get API key
        val apiKey = credentialsRepository.get()
            ?: return FeedsResponse(feeds = emptyList(), total = 0)

        // Fetch feeds from transit.land API - use coordinates for Canadian cities
        val scheduledFeeds = try {
            val coordinates = getRegionCoordinates(regionOnestopId)
            if (coordinates != null) {
                logger.debug("Using coordinate-based search for $regionName: lat=${coordinates.lat}, lon=${coordinates.lon}, radius=${coordinates.radius}m")
                transitLandAPI.feedsByCoordinates(
                    apiKey = apiKey,
                    lat = coordinates.lat,
                    lon = coordinates.lon,
                    radius = coordinates.radius
                ).getOrElse { emptyList() }
            } else {
                logger.debug("Using text-based search for $regionName")
                feedDataSource.feeds(regionName).mapNotNull { result -> result.getOrNull() }
            }
        } catch (e: Exception) {
            logger.error("Failed to fetch feeds for region $regionName", e)
            emptyList()
        }

        logger.info("Found ${scheduledFeeds.size} feeds for region $regionName")

        // Convert to DTOs
        var feeds = scheduledFeeds.map { scheduledFeed ->
            // Generate a human-readable name from the feed ID
            val feedName = generateFeedName(scheduledFeed.feed.uid, regionName)

            FeedDTO(
                feedOnestopId = scheduledFeed.feed.uid,
                regionOnestopId = regionOnestopId,
                name = feedName,
                specType = FeedSpecType.GTFS, // Transit.land feeds are GTFS by default
                downloadUrl = scheduledFeed.feed.url,
                currentVersionSha1 = scheduledFeed.version.uid,
                lastCheckedAt = null,
                lastUpdatedAt = null,
                status = FeedStatus.ACTIVE,
                hasAuthentication = false,
                createdAt = Instant.now(),
                updatedAt = Instant.now()
            )
        }

        // Apply filters
        if (specType != null) {
            feeds = feeds.filter { it.specType == specType }
        }
        if (status != null) {
            feeds = feeds.filter { it.status == status }
        }

        return FeedsResponse(
            feeds = feeds,
            total = feeds.size
        )
    }

    /**
     * Extracts a readable region name from a onestop ID
     * Examples:
     * - "r-montreal-qc" -> "Montreal"
     * - "r-toronto-on" -> "Toronto"
     * - "r-sf-bay-area" -> "San Francisco"
     */
    private fun extractRegionName(regionOnestopId: String): String {
        return when {
            regionOnestopId.contains("montreal") -> "Montreal"
            regionOnestopId.contains("toronto") -> "Toronto"
            regionOnestopId.contains("vancouver") -> "Vancouver"
            regionOnestopId.contains("calgary") -> "Calgary"
            regionOnestopId.contains("ottawa") -> "Ottawa"
            regionOnestopId.contains("winnipeg") -> "Winnipeg"
            regionOnestopId.contains("edmonton") -> "Edmonton"
            regionOnestopId.contains("sf") || regionOnestopId.contains("bay-area") -> "San Francisco"
            regionOnestopId.contains("nyc") || regionOnestopId.contains("new-york") -> "New York"
            regionOnestopId.contains("la") || regionOnestopId.contains("los-angeles") -> "Los Angeles"
            else -> regionOnestopId.removePrefix("r-").replace("-", " ").replaceFirstChar { it.uppercase() }
        }
    }

    /**
     * Gets coordinates for a region to enable geographic-based feed searches.
     * Returns null if the region should use text-based search instead.
     */
    private data class RegionCoordinates(val lat: Double, val lon: Double, val radius: Int = 50000)

    private fun getRegionCoordinates(regionOnestopId: String): RegionCoordinates? {
        return when {
            regionOnestopId.contains("montreal") -> RegionCoordinates(45.5017, -73.5673, 50000) // Montreal, QC
            regionOnestopId.contains("toronto") -> RegionCoordinates(43.6532, -79.3832, 50000) // Toronto, ON
            regionOnestopId.contains("vancouver") -> RegionCoordinates(49.2827, -123.1207, 50000) // Vancouver, BC
            regionOnestopId.contains("calgary") -> RegionCoordinates(51.0447, -114.0719, 50000) // Calgary, AB
            regionOnestopId.contains("ottawa") -> RegionCoordinates(45.4215, -75.6972, 50000) // Ottawa, ON
            regionOnestopId.contains("winnipeg") -> RegionCoordinates(49.8951, -97.1384, 50000) // Winnipeg, MB
            regionOnestopId.contains("edmonton") -> RegionCoordinates(53.5461, -113.4938, 50000) // Edmonton, AB
            else -> null // Use text search for US cities
        }
    }

    /**
     * Generates a human-readable feed name from a feed onestop ID
     * Examples:
     * - "f-f25d-socitdetransportdemontral" with region "Montreal" -> "STM Montreal"
     * - "f-abc123-ttc" with region "Toronto" -> "TTC Toronto"
     */
    private fun generateFeedName(feedOnestopId: String, regionName: String): String {
        // Extract the operator code from the feed ID (everything after the last dash)
        val parts = feedOnestopId.split("-")
        val operatorCode = if (parts.size > 2) parts.last() else feedOnestopId

        // Try to create a readable name from the operator code
        return when {
            operatorCode.contains("ttc", ignoreCase = true) -> "TTC $regionName"
            operatorCode.contains("stm", ignoreCase = true) ||
                feedOnestopId.contains("socitdetransportdemontral", ignoreCase = true) -> "STM $regionName"
            operatorCode.contains("rtm", ignoreCase = true) -> "RTM $regionName"
            operatorCode.contains("translink", ignoreCase = true) -> "TransLink $regionName"
            operatorCode.contains("octranspo", ignoreCase = true) -> "OC Transpo $regionName"
            operatorCode.contains("calgarytransit", ignoreCase = true) -> "Calgary Transit"
            else -> "$regionName Transit (${feedOnestopId.take(12)}...)"
        }
    }

    /**
     * Update region configuration
     *
     * PATCH /api/feed-management/regions/{regionOnestopId}
     */
    @PatchMapping("/{regionOnestopId}")
    fun updateRegion(
        @PathVariable regionOnestopId: String,
        @RequestBody request: RegionUpdateRequest
    ): MetropolitanRegionDTO {
        // Mock update - in real implementation, update database
        return createRegion(
            regionOnestopId,
            "Updated Region",
            request.autoUpdateEnabled ?: true,
            10
        )
    }

    // Helper functions
    private fun createRegion(
        id: String,
        name: String,
        autoUpdate: Boolean,
        feedCount: Int
    ): MetropolitanRegionDTO {
        val now = Instant.now()
        return MetropolitanRegionDTO(
            regionOnestopId = id,
            name = name,
            autoUpdateEnabled = autoUpdate,
            feedCount = feedCount,
            lastCheckAt = now.minusSeconds(3600), // 1 hour ago
            createdAt = now.minusSeconds(86400 * 30), // 30 days ago
            updatedAt = now
        )
    }

}
