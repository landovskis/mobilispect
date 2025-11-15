package com.mobilispect.backend.feed.batch.discovery

import com.mobilispect.backend.feed.model.FeedEntity
import com.mobilispect.backend.feed.model.FeedStatus
import com.mobilispect.backend.feed.model.MetropolitanRegion
import com.mobilispect.backend.feed.repository.FeedRepository
import com.mobilispect.backend.feed.repository.MetropolitanRegionRepository
import org.slf4j.LoggerFactory
import org.springframework.batch.core.configuration.annotation.StepScope
import org.springframework.batch.item.Chunk
import org.springframework.batch.item.ItemWriter
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.time.Instant

/**
 * Spring Batch ItemWriter that persists discovered feeds and their regions to the database.
 *
 * This writer:
 * 1. Creates or updates metropolitan regions from feed discovery results
 * 2. Creates or updates feed entities with complete metadata
 * 3. Establishes many-to-many relationships between feeds and regions
 * 4. Updates timestamps for discovery tracking
 *
 * The writer is transactional and will rollback all changes if any error occurs
 * during the write operation. It handles both new feeds (insert) and existing
 * feeds (update) appropriately.
 *
 * Duplicate regions are deduplicated automatically - if multiple feeds belong
 * to the same region, the region is only created once.
 */
@Component
@StepScope
class FeedDiscoveryWriter(
    private val feedRepository: FeedRepository,
    private val regionRepository: MetropolitanRegionRepository
) : ItemWriter<FeedDiscoveryBatch> {

    private val logger = LoggerFactory.getLogger(FeedDiscoveryWriter::class.java)

    @Transactional
    override fun write(chunk: Chunk<out FeedDiscoveryBatch>) {
        val batches = chunk.items

        logger.info("Writing {} feed discovery batches to database", batches.size)

        var totalFeedsProcessed = 0
        var totalRegionsProcessed = 0
        var feedsCreated = 0
        var feedsUpdated = 0
        var regionsCreated = 0
        var regionsUpdated = 0

        for (batch in batches) {
            logger.debug("Processing batch with {} feed results", batch.results.size)

            // Step 1: Collect all unique regions from the batch
            val uniqueRegions = batch.results
                .map { it.region }
                .distinctBy { it.regionOnestopId }

            logger.debug("Found {} unique regions in batch", uniqueRegions.size)

            // Step 2: Create or update regions
            val regionEntities = mutableMapOf<String, MetropolitanRegion>()

            for (regionMetadata in uniqueRegions) {
                val existingRegion = regionRepository.findById(regionMetadata.regionOnestopId)
                    .orElse(null)

                val regionEntity = if (existingRegion != null) {
                    // Update existing region
                    logger.debug("Updating existing region: {}", regionMetadata.regionOnestopId)
                    regionsUpdated++
                    existingRegion.apply {
                        name = regionMetadata.regionName
                        adm0Name = regionMetadata.adm0Name
                        adm1Name = regionMetadata.adm1Name
                        updatedAt = Instant.now()
                    }
                } else {
                    // Create new region
                    logger.debug("Creating new region: {}", regionMetadata.regionOnestopId)
                    regionsCreated++
                    MetropolitanRegion(
                        regionOnestopId = regionMetadata.regionOnestopId,
                        name = regionMetadata.regionName,
                        adm0Name = regionMetadata.adm0Name,
                        adm1Name = regionMetadata.adm1Name,
                        autoUpdateEnabled = true
                    )
                }

                val savedRegion = regionRepository.save(regionEntity)
                regionEntities[regionMetadata.regionOnestopId] = savedRegion
            }

            totalRegionsProcessed += uniqueRegions.size

            // Step 3: Create or update feeds
            for (result in batch.results) {
                val existingFeed = feedRepository.findById(result.feedOnestopId)
                    .orElse(null)

                val now = Instant.now()

                val feedEntity = if (existingFeed != null) {
                    // Update existing feed
                    logger.debug("Updating existing feed: {}", result.feedOnestopId)
                    feedsUpdated++
                    existingFeed.apply {
                        name = result.name
                        specType = result.specType
                        downloadUrl = result.downloadUrl
                        staticFeedUrl = result.staticFeedUrl
                        realtimeFeedUrl = result.realtimeFeedUrl
                        currentVersionSha1 = result.versionSha1
                        lastDiscoveredAt = now
                        updatedAt = now

                        // Update region associations
                        val regionEntity = regionEntities[result.region.regionOnestopId]
                        if (regionEntity != null && !regions.contains(regionEntity)) {
                            regions.add(regionEntity)
                        }
                    }
                } else {
                    // Create new feed
                    logger.debug("Creating new feed: {}", result.feedOnestopId)
                    feedsCreated++
                    val regionEntity = regionEntities[result.region.regionOnestopId]
                        ?: error("Region entity not found for ${result.region.regionOnestopId}")

                    FeedEntity(
                        feedOnestopId = result.feedOnestopId,
                        name = result.name,
                        specType = result.specType,
                        downloadUrl = result.downloadUrl,
                        staticFeedUrl = result.staticFeedUrl,
                        realtimeFeedUrl = result.realtimeFeedUrl,
                        currentVersionSha1 = result.versionSha1,
                        status = FeedStatus.ACTIVE,
                        lastDiscoveredAt = now,
                        lastCheckedAt = now,
                        lastUpdatedAt = now
                    ).apply {
                        regions.add(regionEntity)
                    }
                }

                val savedFeed = feedRepository.save(feedEntity)
                totalFeedsProcessed++
            }
        }

        logger.info(
            "Successfully wrote to database: {} feeds ({} created, {} updated), {} regions ({} created, {} updated)",
            totalFeedsProcessed,
            feedsCreated,
            feedsUpdated,
            totalRegionsProcessed,
            regionsCreated,
            regionsUpdated
        )
    }
}

/**
 * Statistics tracking for feed discovery writes.
 *
 * This data class captures metrics about the write operation for monitoring
 * and reporting purposes.
 */
data class FeedDiscoveryWriteStats(
    val totalFeedsProcessed: Int = 0,
    val feedsCreated: Int = 0,
    val feedsUpdated: Int = 0,
    val totalRegionsProcessed: Int = 0,
    val regionsCreated: Int = 0,
    val regionsUpdated: Int = 0,
    val errors: List<String> = emptyList()
) {
    fun toLogString(): String {
        return "feeds: $totalFeedsProcessed ($feedsCreated created, $feedsUpdated updated), " +
                "regions: $totalRegionsProcessed ($regionsCreated created, $regionsUpdated updated), " +
                "errors: ${errors.size}"
    }
}
