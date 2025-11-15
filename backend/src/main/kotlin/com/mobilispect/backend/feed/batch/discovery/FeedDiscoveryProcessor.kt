package com.mobilispect.backend.feed.batch.discovery

import org.slf4j.LoggerFactory
import org.springframework.batch.core.configuration.annotation.StepScope
import org.springframework.batch.item.ItemProcessor
import org.springframework.stereotype.Component

/**
 * Spring Batch ItemProcessor that combines feed region data with feed metadata
 * to produce complete feed discovery results.
 *
 * This processor:
 * 1. Takes FeedDiscoveryInput containing both FeedRegionMap and FeedMetadataMap
 * 2. Joins the two maps on feed ID
 * 3. Creates FeedDiscoveryResult for each feed with complete information
 * 4. Returns FeedDiscoveryBatch containing all successfully matched feeds
 *
 * Feeds that have region data but no metadata, or metadata but no region data,
 * are logged as warnings and excluded from the output.
 *
 * This processor is a critical integration point in the feed discovery pipeline,
 * ensuring that only feeds with complete information proceed to the import stage.
 */
@Component
@StepScope
class FeedDiscoveryProcessor : ItemProcessor<FeedDiscoveryInput, FeedDiscoveryBatch> {

    private val logger = LoggerFactory.getLogger(FeedDiscoveryProcessor::class.java)

    override fun process(item: FeedDiscoveryInput): FeedDiscoveryBatch {
        val feedRegionMap = item.feedRegionMap
        val feedMetadataMap = item.feedMetadataMap

        logger.info(
            "Processing feed discovery batch: {} feeds with regions, {} feeds with metadata",
            feedRegionMap.size,
            feedMetadataMap.size
        )

        // Get all feed IDs from both maps
        val allFeedIds = (feedRegionMap.feedIds() + feedMetadataMap.feedIds()).distinct()

        val results = mutableListOf<FeedDiscoveryResult>()
        var missingMetadataCount = 0
        var missingRegionCount = 0
        var missingDownloadUrlCount = 0

        for (feedId in allFeedIds) {
            val regionMetadata = feedRegionMap[feedId]
            val feedMetadata = feedMetadataMap[feedId]

            when {
                regionMetadata == null && feedMetadata == null -> {
                    // Should not happen, but log it
                    logger.error("Feed {} found in join but missing from both maps", feedId)
                }
                regionMetadata == null -> {
                    logger.warn("Feed {} has metadata but no region information", feedId)
                    missingRegionCount++
                }
                feedMetadata == null -> {
                    logger.warn("Feed {} has region information but no metadata", feedId)
                    missingMetadataCount++
                }
                feedMetadata.downloadUrl.isBlank() || !isValidDownloadUrl(feedMetadata.downloadUrl) -> {
                    // Filter out feeds without a valid HTTP(S) download URL - they can't be imported
                    logger.warn(
                        "Feed {} has invalid download URL '{}', skipping",
                        feedId,
                        feedMetadata.downloadUrl
                    )
                    missingDownloadUrlCount++
                }
                else -> {
                    // Both present and download URL exists - create complete result
                    val result = FeedDiscoveryResult(
                        feedOnestopId = feedId,
                        name = feedMetadata.name,
                        downloadUrl = feedMetadata.downloadUrl,
                        specType = feedMetadata.specType,
                        versionSha1 = feedMetadata.versionSha1,
                        earliestCalendarDate = feedMetadata.earliestCalendarDate,
                        latestCalendarDate = feedMetadata.latestCalendarDate,
                        region = regionMetadata,
                        staticFeedUrl = feedMetadata.staticFeedUrl,
                        realtimeFeedUrl = feedMetadata.realtimeFeedUrl,
                        authorizationType = feedMetadata.authorizationType,
                        authorizationInfoUrl = feedMetadata.authorizationInfoUrl
                    )
                    results.add(result)
                }
            }
        }

        logger.info(
            "Feed discovery processing complete: {} successful matches, {} missing metadata, {} missing region, {} missing download URL",
            results.size,
            missingMetadataCount,
            missingRegionCount,
            missingDownloadUrlCount
        )

        val totalSkipped = missingMetadataCount + missingRegionCount + missingDownloadUrlCount
        if (totalSkipped > 0) {
            logger.warn(
                "Data quality issue: {}/{} feeds had incomplete information ({}% success rate)",
                totalSkipped,
                allFeedIds.size,
                if (allFeedIds.isNotEmpty()) (results.size * 100) / allFeedIds.size else 0
            )
        }

        return FeedDiscoveryBatch(results)
    }

    /**
     * Validates that a download URL is a proper HTTP(S) URL.
     *
     * The database constraint requires download URLs to be either empty or start with http(s)://
     * This method filters out invalid URLs like local paths, temporary files, etc.
     *
     * @param url The download URL to validate
     * @return true if the URL is a valid HTTP(S) URL, false otherwise
     */
    private fun isValidDownloadUrl(url: String): Boolean {
        return url.startsWith("http://") || url.startsWith("https://")
    }
}

/**
 * Alternate processor that takes separate FeedRegionMap and FeedMetadataMap inputs
 * and combines them into FeedDiscoveryInput before processing.
 *
 * This is useful when the batch job has separate steps producing region and metadata maps.
 */
@Component
@StepScope
class FeedDiscoveryCombiningProcessor(
    private val feedDiscoveryProcessor: FeedDiscoveryProcessor
) : ItemProcessor<Pair<FeedRegionMap, FeedMetadataMap>, FeedDiscoveryBatch> {

    private val logger = LoggerFactory.getLogger(FeedDiscoveryCombiningProcessor::class.java)

    override fun process(item: Pair<FeedRegionMap, FeedMetadataMap>): FeedDiscoveryBatch? {
        val (feedRegionMap, feedMetadataMap) = item

        logger.debug(
            "Combining feed region map ({} feeds) with feed metadata map ({} feeds)",
            feedRegionMap.size,
            feedMetadataMap.size
        )

        val input = FeedDiscoveryInput(
            feedRegionMap = feedRegionMap,
            feedMetadataMap = feedMetadataMap
        )

        return feedDiscoveryProcessor.process(input)
    }
}
