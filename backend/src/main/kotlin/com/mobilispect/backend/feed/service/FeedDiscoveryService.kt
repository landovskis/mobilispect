package com.mobilispect.backend.feed.service

import com.mobilispect.backend.feed.model.FeedEntity
import com.mobilispect.backend.feed.model.FeedSpecType
import com.mobilispect.backend.feed.model.FeedStatus
import com.mobilispect.backend.feed.repository.FeedRepository
import com.mobilispect.backend.feed.repository.MetropolitanRegionRepository
import com.mobilispect.backend.schedule.ScheduledFeed
import com.mobilispect.backend.schedule.transit_land.TransitLandAPI
import com.mobilispect.backend.schedule.transit_land.api.TransitLandCredentialsRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant

data class FeedDiscoveryResult(
    val regionOnestopId: String,
    val feedsDiscovered: Int,
    val feedsCreated: Int,
    val feedsUpdated: Int,
    val errors: List<String>
)

@Service
class FeedDiscoveryService(
    private val regionRepository: MetropolitanRegionRepository,
    private val feedRepository: FeedRepository,
    private val transitLandAPI: TransitLandAPI,
    private val credentialsRepository: TransitLandCredentialsRepository
) {
    private val logger = LoggerFactory.getLogger(FeedDiscoveryService::class.java)

    @Transactional
    fun discover(regionOnestopId: String, specType: FeedSpecType = FeedSpecType.GTFS): FeedDiscoveryResult {
        val region = regionRepository.findById(regionOnestopId)
            .orElseThrow { IllegalArgumentException("Region not found: $regionOnestopId") }

        val apiKey = credentialsRepository.get()
            ?: throw IllegalStateException("Transit.land API key is not configured")

        val feedsResult = transitLandAPI.feeds(apiKey, region.name, specType.dbValue)

        val errors = mutableListOf<String>()
        var created = 0
        var updated = 0

        val scheduledFeeds = feedsResult.getOrElse { throwable ->
            logger.error("Failed to fetch feeds for region {}", regionOnestopId, throwable)
            errors.add(throwable.message ?: "Unknown error")
            emptyList()
        }

        scheduledFeeds.forEach { scheduledFeed ->
            try {
                val result = upsertFeed(region.regionOnestopId, scheduledFeed, specType)
                when (result) {
                    UpsertResult.CREATED -> created++
                    UpsertResult.UPDATED -> updated++
                    UpsertResult.NO_CHANGE -> {}
                }
            } catch (ex: Exception) {
                logger.error("Failed to store feed {}", scheduledFeed.feed.uid, ex)
                errors.add("Failed to store ${scheduledFeed.feed.uid}: ${ex.message ?: "unknown error"}")
            }
        }

        return FeedDiscoveryResult(
            regionOnestopId = regionOnestopId,
            feedsDiscovered = scheduledFeeds.size,
            feedsCreated = created,
            feedsUpdated = updated,
            errors = errors
        )
    }

    private enum class UpsertResult {
        CREATED,
        UPDATED,
        NO_CHANGE
    }

    private fun upsertFeed(
        regionOnestopId: String,
        scheduledFeed: ScheduledFeed,
        specType: FeedSpecType
    ): UpsertResult {
        val now = Instant.now()
        val existing = feedRepository.findById(scheduledFeed.feed.uid)

        val entity = existing.orElseGet {
            FeedEntity(
                feedOnestopId = scheduledFeed.feed.uid,
                region = regionRepository.getReferenceById(regionOnestopId)
            ).apply {
                name = inferFeedName(scheduledFeed.feed.uid)
            }
        }

        val originalSha1 = entity.currentVersionSha1

        entity.region = regionRepository.getReferenceById(regionOnestopId)
        entity.name = entity.name.ifBlank { inferFeedName(scheduledFeed.feed.uid) }
        entity.specType = specType
        entity.downloadUrl = scheduledFeed.feed.url
        entity.currentVersionSha1 = scheduledFeed.version.uid
        entity.lastCheckedAt = now
        entity.status = FeedStatus.ACTIVE

        if (entity.currentVersionSha1 != originalSha1) {
            entity.lastUpdatedAt = now
        }

        feedRepository.save(entity)

        return when {
            existing.isEmpty -> UpsertResult.CREATED
            entity.currentVersionSha1 != originalSha1 -> UpsertResult.UPDATED
            else -> UpsertResult.NO_CHANGE
        }
    }

    private fun inferFeedName(feedOnestopId: String): String {
        val parts = feedOnestopId.split("-")
        return when {
            parts.size >= 3 -> parts.drop(2).joinToString(" ") { it.replaceFirstChar(Char::uppercaseChar) }
            else -> feedOnestopId
        }
    }
}
