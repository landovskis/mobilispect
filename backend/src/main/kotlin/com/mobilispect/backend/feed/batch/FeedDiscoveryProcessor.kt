package com.mobilispect.backend.feed.batch

import com.mobilispect.backend.feed.integration.PlaceSummary
import com.mobilispect.backend.feed.integration.TransitLandFeedSummary
import com.mobilispect.backend.feed.model.FeedEntity
import com.mobilispect.backend.feed.model.FeedStatus
import com.mobilispect.backend.feed.model.MetropolitanRegion
import com.mobilispect.backend.feed.repository.FeedRepository
import com.mobilispect.backend.feed.repository.MetropolitanRegionRepository
import org.slf4j.LoggerFactory
import org.springframework.batch.core.configuration.annotation.StepScope
import org.springframework.batch.item.ItemProcessor
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import java.time.Clock
import java.time.Instant

/**
 * ItemProcessor for feed discovery batch processing.
 *
 * Transforms TransitLandFeedSummary into FeedEntity, handling:
 * - Feed creation vs. update logic
 * - Multi-region assignment based on operator places
 * - Feed name inference from onestop ID
 * - Download URL selection (static vs realtime vs version URL)
 *
 * Returns null to skip feeds that should not be persisted.
 */
@Component
@StepScope
class FeedDiscoveryProcessor(
    private val feedRepository: FeedRepository,
    private val regionRepository: MetropolitanRegionRepository,
    private val clock: Clock = Clock.systemUTC(),
    @Value("#{jobParameters['regionOnestopId'] ?: null}") private val regionOnestopId: String?
) : ItemProcessor<TransitLandFeedSummary, FeedEntity> {

    private val logger = LoggerFactory.getLogger(FeedDiscoveryProcessor::class.java)

    override fun process(item: TransitLandFeedSummary): FeedEntity? {
        val now = Instant.now(clock)

        return try {
            // Determine regions for this feed
            val regionIds = if (regionOnestopId != null) {
                listOf(regionOnestopId)
            } else {
                extractRegionsFromPlaces(item.feedOnestopId, item.name, item.places)
            }

            // Validate all regions exist
            regionIds.forEach { regionId ->
                if (!regionRepository.existsById(regionId)) {
                    logger.warn("Region {} does not exist for feed {}, skipping", regionId, item.feedOnestopId)
                    return null
                }
            }

            // Get region references
            val regionRefs = regionIds.map { regionRepository.getReferenceById(it) }.toMutableSet()

            // Check if feed exists
            val existing = feedRepository.findById(item.feedOnestopId)

            val feed = existing.orElseGet {
                // Create new feed
                logger.info("Creating new feed: {}", item.feedOnestopId)
                FeedEntity(
                    feedOnestopId = item.feedOnestopId,
                    regions = regionRefs,
                    name = item.name.ifBlank { inferFeedName(item.feedOnestopId) },
                    specType = item.specType,
                    downloadUrl = selectDownloadUrl(item, ""),
                    currentVersionSha1 = item.latestVersionSha1 ?: "",
                    lastCheckedAt = now,
                    lastDiscoveredAt = now,
                    lastUpdatedAt = now,
                    status = FeedStatus.ACTIVE
                )
            }

            // Update existing feed
            if (existing.isPresent) {
                logger.debug("Updating existing feed: {}", item.feedOnestopId)
                val originalSha1 = feed.currentVersionSha1
                val originalRegions = feed.regions.map { it.regionOnestopId }.toSet()

                // Update regions
                feed.regions.clear()
                feed.regions.addAll(regionRefs)

                // Update metadata
                feed.name = item.name.ifBlank { inferFeedName(item.feedOnestopId) }
                feed.specType = item.specType
                feed.downloadUrl = selectDownloadUrl(item, feed.downloadUrl)
                feed.currentVersionSha1 = item.latestVersionSha1 ?: feed.currentVersionSha1
                feed.lastCheckedAt = now

                // Only update lastUpdatedAt if meaningful changes occurred
                val currentRegions = feed.regions.map { it.regionOnestopId }.toSet()
                val hasChanges = (feed.currentVersionSha1 != originalSha1) ||
                    (originalRegions != currentRegions)

                if (hasChanges) {
                    feed.lastUpdatedAt = now
                    logger.info("Feed {} has updates (SHA1 or regions changed)", item.feedOnestopId)
                }
            }

            feed
        } catch (ex: Exception) {
            logger.error("Failed to process feed {}", item.feedOnestopId, ex)
            null // Skip this feed
        }
    }

    /**
     * Extract region onestop IDs from operator geographic places.
     * Creates one region per unique (adm0_name, adm1_name, city_name) triple.
     * Falls back to feed onestop ID parsing if no places data is available.
     */
    private fun extractRegionsFromPlaces(
        feedOnestopId: String,
        feedName: String,
        places: List<PlaceSummary>
    ): List<String> {
        if (places.isEmpty()) {
            logger.debug("No places data for feed {}, using feed onestop ID fallback", feedOnestopId)
            return listOf(extractRegionFromFeedOnestopId(feedOnestopId))
        }

        val regionIds = places.mapNotNull { place ->
            buildRegionOnestopId(place.adm0Name, place.adm1Name, place.cityName)
        }.distinct()

        if (regionIds.isEmpty()) {
            logger.warn("Could not extract regions from places for feed {}, using fallback", feedOnestopId)
            return listOf(extractRegionFromFeedOnestopId(feedOnestopId))
        }

        logger.debug("Extracted {} regions for feed {}: {}", regionIds.size, feedOnestopId, regionIds)
        return regionIds
    }

    /**
     * Build a region onestop ID from geographic components.
     * Format: r-{country}-{state/province}-{city}
     */
    private fun buildRegionOnestopId(adm0Name: String?, adm1Name: String?, cityName: String?): String? {
        val components = listOfNotNull(adm0Name, adm1Name, cityName)
            .filter { it.isNotBlank() }
            .map { it.lowercase().replace(Regex("[^a-z0-9]+"), "-") }

        if (components.isEmpty()) {
            return null
        }

        return "r-${components.joinToString("-")}"
    }

    /**
     * Extract region from feed onestop ID as fallback.
     * Feed IDs like "f-9q9-caltrain" → "r-9q9-auto"
     */
    private fun extractRegionFromFeedOnestopId(feedOnestopId: String): String {
        val parts = feedOnestopId.split("-")
        return if (parts.size >= 2) {
            "r-${parts[1]}-auto"
        } else {
            "r-unknown-auto"
        }
    }

    /**
     * Select the appropriate download URL based on feed spec type and available URLs.
     */
    private fun selectDownloadUrl(summary: TransitLandFeedSummary, fallback: String): String {
        return when (summary.specType) {
            com.mobilispect.backend.feed.model.FeedSpecType.GTFS -> summary.staticFeedUrl
                ?: summary.latestVersionUrl
                ?: fallback.takeIf { it.isNotBlank() }
                ?: ""

            com.mobilispect.backend.feed.model.FeedSpecType.GTFS_RT -> summary.realtimeFeedUrl
                ?: fallback.takeIf { it.isNotBlank() }
                ?: ""
        }
    }

    /**
     * Infer a human-readable feed name from the onestop ID.
     * Examples:
     * - "f-9q9-caltrain" → "Caltrain"
     * - "f-sf~bay~area~bart" → "Bart"
     */
    private fun inferFeedName(feedOnestopId: String): String {
        val parts = feedOnestopId.split("-", "~")
        return if (parts.size >= 2) {
            parts.drop(2).joinToString(" ") { it.replaceFirstChar { c -> c.uppercase() } }
        } else {
            feedOnestopId
        }
    }
}
