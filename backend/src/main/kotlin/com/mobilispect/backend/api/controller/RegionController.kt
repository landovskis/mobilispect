package com.mobilispect.backend.api.controller

import com.mobilispect.backend.api.dto.*
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
class RegionController {

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
        // Mock feeds data for each region
        val allFeeds = when (regionOnestopId) {
            "r-toronto-on" -> listOf(
                createFeed("f-toronto-ttc~rt", regionOnestopId, "Toronto TTC", FeedSpecType.GTFS),
                createFeed("f-toronto-go~rt", regionOnestopId, "GO Transit", FeedSpecType.GTFS),
                createFeed("f-toronto-ttc-realtime", regionOnestopId, "TTC Real-time", FeedSpecType.GTFS_RT)
            )
            "r-vancouver-bc" -> listOf(
                createFeed("f-vancouver-translink~rt", regionOnestopId, "TransLink", FeedSpecType.GTFS),
                createFeed("f-vancouver-translink-realtime", regionOnestopId, "TransLink Real-time", FeedSpecType.GTFS_RT)
            )
            "r-montreal-qc" -> listOf(
                createFeed("f-montreal-stm~rt", regionOnestopId, "STM Montreal", FeedSpecType.GTFS),
                createFeed("f-montreal-rtm~rt", regionOnestopId, "RTM Montreal", FeedSpecType.GTFS),
                createFeed("f-montreal-stm-realtime", regionOnestopId, "STM Real-time", FeedSpecType.GTFS_RT)
            )
            "r-calgary-ab" -> listOf(
                createFeed("f-calgary-transit~rt", regionOnestopId, "Calgary Transit", FeedSpecType.GTFS)
            )
            "r-ottawa-on" -> listOf(
                createFeed("f-ottawa-octranspo~rt", regionOnestopId, "OC Transpo", FeedSpecType.GTFS)
            )
            "r-sf-bay-area" -> listOf(
                createFeed("f-sf-bay-area~rt", regionOnestopId, "SF Bay Area", FeedSpecType.GTFS)
            )
            else -> emptyList()
        }

        var filteredFeeds = allFeeds
        if (specType != null) {
            filteredFeeds = filteredFeeds.filter { it.specType == specType }
        }
        if (status != null) {
            filteredFeeds = filteredFeeds.filter { it.status == status }
        }

        return FeedsResponse(
            feeds = filteredFeeds,
            total = filteredFeeds.size
        )
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

    private fun createFeed(
        id: String,
        regionId: String,
        name: String,
        specType: FeedSpecType
    ): FeedDTO {
        val now = Instant.now()
        return FeedDTO(
            feedOnestopId = id,
            regionOnestopId = regionId,
            name = name,
            specType = specType,
            downloadUrl = "https://example.com/feeds/$id",
            currentVersionSha1 = UUID.randomUUID().toString().replace("-", ""),
            lastCheckedAt = now.minusSeconds(3600),
            lastUpdatedAt = now.minusSeconds(86400),
            status = FeedStatus.ACTIVE,
            hasAuthentication = false,
            createdAt = now.minusSeconds(86400 * 30),
            updatedAt = now
        )
    }
}
