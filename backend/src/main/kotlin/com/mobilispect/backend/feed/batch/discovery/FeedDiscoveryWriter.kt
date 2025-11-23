package com.mobilispect.backend.feed.batch.discovery

import com.mobilispect.backend.feed.model.FeedEntity
import com.mobilispect.backend.feed.model.ids.FeedId
import com.mobilispect.backend.feed.model.FeedStatus
import com.mobilispect.backend.feed.model.MetropolitanRegion
import com.mobilispect.backend.feed.repository.FeedRepository
import com.mobilispect.backend.feed.model.ids.RegionId
import com.mobilispect.backend.feed.repository.MetropolitanRegionRepository
import org.slf4j.LoggerFactory
import org.springframework.batch.core.StepExecution
import org.springframework.batch.core.annotation.BeforeStep
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
    private var stepExecution: StepExecution? = null
    private var cumulativeFeedsProcessed = 0
    private val cumulativeRegionIds = mutableSetOf<String>()

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

        val chunkRegionIds = mutableSetOf<String>()

        for ((batchIndex, batch) in batches.withIndex()) {
            logger.info("  Batch {}/{}: Processing {} feeds", batchIndex + 1, batches.size, batch.results.size)
            val feedsCreatedBeforeBatch = feedsCreated

            // Step 1: Collect all unique regions from the batch
            val uniqueRegions = batch.results
                .map { it.region }
                .distinctBy { it.regionOnestopId }

            logger.info("    → Found {} unique regions", uniqueRegions.size)

            // Step 2: Create or update regions
            val regionEntities = mutableMapOf<String, MetropolitanRegion>()

            for (regionMetadata in uniqueRegions) {
                val existingRegion = regionRepository.findByRegionOnestopId(RegionId(regionMetadata.regionOnestopId))
                    .orElse(null)

                val regionEntity = if (existingRegion != null) {
                    // Update existing region
                    regionsUpdated++
                    existingRegion.apply {
                        name = regionMetadata.regionName
                        adm0Name = regionMetadata.adm0Name
                        adm1Name = regionMetadata.adm1Name
                        updatedAt = Instant.now()
                    }
                } else {
                    // Create new region
                    logger.info("      ✓ Creating region: {}", regionMetadata.regionName)
                    regionsCreated++
                    MetropolitanRegion(
                        regionOnestopId = RegionId(regionMetadata.regionOnestopId),
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
                val feedId = result.feedOnestopId
                val existingFeed = feedRepository.findById(feedId)
                    .orElse(null)

                val now = Instant.now()

                val feedEntity = if (existingFeed != null) {
                    // Update existing feed
                    feedsUpdated++
                    existingFeed.apply {
                        name = result.name
                        specType = result.specType
                        downloadUrl = result.downloadUrl
                        staticFeedUrl = result.staticFeedUrl
                        realtimeFeedUrl = result.realtimeFeedUrl
                        currentVersionSha1 = result.versionSha1
                        operatorName = result.region.operatorName
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
                    logger.info("      ✓ Creating feed: {} ({})", result.name, result.region.operatorName ?: "unknown operator")
                    feedsCreated++
                    val regionEntity = regionEntities[result.region.regionOnestopId]
                        ?: error("Region entity not found for ${result.region.regionOnestopId}")

                    FeedEntity(
                        feedOnestopId = feedId,
                        name = result.name,
                        specType = result.specType,
                        downloadUrl = result.downloadUrl,
                        staticFeedUrl = result.staticFeedUrl,
                        realtimeFeedUrl = result.realtimeFeedUrl,
                        operatorName = result.region.operatorName,
                        currentVersionSha1 = result.versionSha1,
                        status = FeedStatus.ACTIVE,
                        lastDiscoveredAt = now,
                        lastCheckedAt = now,
                        lastUpdatedAt = now
                    ).apply {
                        regions.add(regionEntity)
                    }
                }

                feedRepository.save(feedEntity)
                totalFeedsProcessed++
                chunkRegionIds.add(result.region.regionOnestopId)
            }

            val batchNewFeeds = feedsCreated - feedsCreatedBeforeBatch
            logger.info("    ✓ Batch complete: {} feeds saved ({} new)", batch.results.size, batchNewFeeds)
        }

        logger.info("""
            ✓ Write complete:
              • Feeds: {} total ({} created, {} updated)
              • Regions: {} total ({} created, {} updated)
        """.trimIndent(),
            totalFeedsProcessed,
            feedsCreated,
            feedsUpdated,
            totalRegionsProcessed,
            regionsCreated,
            regionsUpdated
        )

        recordMetrics(totalFeedsProcessed, chunkRegionIds)
    }

    @BeforeStep
    fun beforeStep(stepExecution: StepExecution) {
        this.stepExecution = stepExecution
    }

    private fun recordMetrics(feedsProcessed: Int, regionIds: Set<String>) {
        if (feedsProcessed == 0 && regionIds.isEmpty()) {
            return
        }
        cumulativeFeedsProcessed += feedsProcessed
        cumulativeRegionIds.addAll(regionIds)
        val regionsFound = cumulativeRegionIds.size
        val context = stepExecution?.executionContext
        context?.putInt("feedsFound", cumulativeFeedsProcessed)
        context?.putInt("regionsFound", regionsFound)
        stepExecution?.jobExecution?.executionContext?.putInt("feedsFound", cumulativeFeedsProcessed)
        stepExecution?.jobExecution?.executionContext?.putInt("regionsFound", regionsFound)
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
