package com.mobilispect.backend.feed.service

import com.mobilispect.backend.feed.model.ids.FeedId
import com.mobilispect.backend.feed.model.ids.RegionId
import com.mobilispect.backend.feed.repository.FeedRepository
import com.mobilispect.backend.schedule.transit_land.TransitLandAPI
import com.mobilispect.backend.schedule.transit_land.api.TransitLandCredentialsRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.security.MessageDigest

/**
 * Service for SHA-1 based feed version detection and change tracking.
 *
 * Task T042: Create FeedVersionService for SHA1-based change detection
 * Per FR-013: System MUST check feeds using SHA-1 content hash comparison
 *
 * This service:
 * 1. Retrieves current version hashes from Transit.land API
 * 2. Compares them with stored hashes in the database
 * 3. Identifies feeds that have been updated
 * 4. Supports both individual feed checks and bulk region checks
 *
 * SHA-1 hashes are computed from complete feed archives to detect any changes
 * in the feed data, ensuring accurate change detection per FR-016.
 */
@Service
class FeedVersionService(
    private val feedRepository: FeedRepository,
    private val transitLandAPI: TransitLandAPI,
    private val credentialsRepository: TransitLandCredentialsRepository
) {
    private val logger = LoggerFactory.getLogger(FeedVersionService::class.java)

    /**
     * Check if a specific feed has been updated by comparing SHA-1 hashes.
     *
     * @param feedOnestopId The Onestop ID of the feed to check
     * @return true if the feed has a new version, false otherwise
     */
    /**
     * Check if a specific feed has been updated by comparing SHA-1 hashes.
     *
     * @param feedOnestopId The Onestop ID of the feed to check
     * @return true if the feed has a new version, false otherwise
     */
    fun hasUpdate(feedOnestopId: FeedId): Boolean {
        val feed = feedRepository.findByFeedOnestopId(feedOnestopId).orElse(null)
        if (feed == null) {
            logger.warn("Feed not found: {}", feedOnestopId.value)
            return false
        }

        val apiKey = credentialsRepository.get()
            ?: throw IllegalStateException("Transit.land API key not configured")

        return try {
            // Fetch current version from Transit.land
            val feedsResult = transitLandAPI.feeds(apiKey, feedOnestopId.value, feed.specType.dbValue)

            val scheduledFeed = feedsResult.getOrNull()?.firstOrNull()
            if (scheduledFeed == null) {
                logger.warn("Feed not found in Transit.land: {}", feedOnestopId)
                return false
            }

            // The version SHA1 is in the FeedVersion object
            val currentVersionHash = scheduledFeed.version.uid

            if (currentVersionHash.isBlank()) {
                logger.warn("Could not retrieve version hash for feed: {}", feedOnestopId)
                return false
            }

            // Compare with stored hash
            val hasUpdate = feed.currentVersionSha1 != currentVersionHash

            if (hasUpdate) {
                logger.info(
                    "Update detected for feed {}: {} -> {}",
                    feedOnestopId,
                    feed.currentVersionSha1 ?: "null",
                    currentVersionHash
                )
            }

            hasUpdate
        } catch (ex: Exception) {
            logger.error("Error checking version for feed: {}", feedOnestopId, ex)
            false
        }
    }

    /**
     * Check all feeds in a region for updates.
     *
     * @param regionOnestopId The Onestop ID of the region
     * @return Result containing counts and list of updated feeds
     */
    fun checkForUpdates(regionOnestopId: RegionId): FeedUpdateCheckResult {
        val feeds = feedRepository.findAllByRegionRegionOnestopId(regionOnestopId)
        logger.debug("Checking {} feeds for region: {}", feeds.size, regionOnestopId)

        val feedsWithUpdates = mutableListOf<FeedId>()
        var errors = 0

        feeds.forEach { feed ->
            try {
                if (hasUpdate(feed.feedOnestopId)) {
                    feedsWithUpdates.add(feed.feedOnestopId)
                }
            } catch (ex: Exception) {
                logger.error("Error checking feed: {}", feed.feedOnestopId, ex)
                errors++
            }
        }

        return FeedUpdateCheckResult(
            regionOnestopId = regionOnestopId,
            feedsChecked = feeds.size,
            updatesDetected = feedsWithUpdates.size,
            feedsWithUpdates = feedsWithUpdates,
            errors = errors
        )
    }

    /**
     * Calculate SHA-1 hash from byte array (used for local verification).
     */
    fun calculateSha1(data: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-1")
        val hashBytes = digest.digest(data)
        return hashBytes.joinToString("") { "%02x".format(it) }
    }
}

/**
 * Result of checking feeds in a region for updates.
 */
data class FeedUpdateCheckResult(
    val regionOnestopId: RegionId,
    val feedsChecked: Int,
    val updatesDetected: Int,
    val feedsWithUpdates: List<FeedId>,
    val errors: Int
)
