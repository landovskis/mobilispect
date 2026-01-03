package com.mobilispect.backend.feed.batch.discovery

import com.mobilispect.backend.feed.domain.model.ids.FeedId
import org.slf4j.LoggerFactory
import org.springframework.batch.core.configuration.annotation.StepScope
import org.springframework.batch.infrastructure.item.ItemProcessor
import org.springframework.stereotype.Component

/**
 * Spring Batch ItemProcessor that combines feed region data with feed metadata to produce complete
 * feed discovery results.
 *
 * This processor:
 * 1. Takes FeedDiscoveryInput containing both FeedRegionMap and FeedMetadataMap
 * 2. Joins the two maps on feed ID
 * 3. Creates FeedDiscoveryResult for each feed with complete information
 * 4. Returns FeedDiscoveryBatch containing all successfully matched feeds
 *
 * Feeds that have region data but no metadata, or metadata but no region data, are logged as
 * warnings and excluded from the output.
 *
 * This processor is a critical integration point in the feed discovery pipeline, ensuring that only
 * feeds with complete information proceed to the import stage.
 */
@Component
@StepScope
class FeedDiscoveryProcessor : ItemProcessor<FeedDiscoveryInput, FeedDiscoveryBatch> {

  private val logger = LoggerFactory.getLogger(FeedDiscoveryProcessor::class.java)

  override fun process(item: FeedDiscoveryInput): FeedDiscoveryBatch {
    val feedRegionMap = item.feedRegionMap
    val feedMetadataMap = item.feedMetadataMap

    logger.debug(
      "  Processing batch: {} feeds with regions, {} with metadata",
      feedRegionMap.size,
      feedMetadataMap.size,
    )

    // Get all feed IDs from both maps
    val allFeedIds: List<FeedId> = (feedRegionMap.feedIds() + feedMetadataMap.feedIds()).distinct()

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
          logger.error("    ✗ Feed {} missing from both maps", feedId.value)
        }
        regionMetadata == null -> {
          logger.debug("    ⚠ Feed {} has metadata but no region", feedId.value)
          missingRegionCount++
        }
        feedMetadata == null -> {
          logger.debug("    ⚠ Feed {} has region but no metadata", feedId.value)
          missingMetadataCount++
        }
        feedMetadata.downloadUrl.isBlank() || !isValidDownloadUrl(feedMetadata.downloadUrl) -> {
          // Filter out feeds without a valid HTTP(S) download URL - they can't be imported
          logger.debug(
            "    ⚠ Feed {} has invalid download URL: '{}'",
            feedId.value,
            feedMetadata.downloadUrl,
          )
          missingDownloadUrlCount++
        }
        else -> {
          // Both present and download URL exists - create complete result
          val result =
            FeedDiscoveryResult(
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
              authorizationInfoUrl = feedMetadata.authorizationInfoUrl,
            )

          // Highlight Montreal feeds
          val isMontreal =
            feedId.value.startsWith("f-f25") ||
              regionMetadata.regionName.contains("Montréal", ignoreCase = true) ||
              regionMetadata.regionName.contains("Montreal", ignoreCase = true) ||
              regionMetadata.operatorName?.contains("STM") == true ||
              regionMetadata.operatorName?.contains("STL") == true ||
              regionMetadata.operatorName?.contains("RTL") == true ||
              regionMetadata.operatorName?.contains("EXO") == true

          if (isMontreal) {
            logger.info("      🍁 Matched: {} → {}", feedMetadata.name, regionMetadata.regionName)
          }

          results.add(result)
        }
      }
    }

    val totalSkipped = missingMetadataCount + missingRegionCount + missingDownloadUrlCount
    if (totalSkipped > 0) {
      val successRate = if (allFeedIds.isNotEmpty()) (results.size * 100) / allFeedIds.size else 0
      logger.info(
        "    ✓ Matched {}/{} feeds ({}% success, {} skipped: {} no-metadata, {} no-region, {} invalid-url)",
        results.size,
        allFeedIds.size,
        successRate,
        totalSkipped,
        missingMetadataCount,
        missingRegionCount,
        missingDownloadUrlCount,
      )
    } else {
      logger.info("    ✓ Matched all {} feeds successfully", results.size)
    }

    return FeedDiscoveryBatch(results)
  }

  /**
   * Validates that a download URL is a proper HTTP(S) URL.
   *
   * The database constraint requires download URLs to be either empty or start with http(s):// This
   * method filters out invalid URLs like local paths, temporary files, etc.
   *
   * @param url The download URL to validate
   * @return true if the URL is a valid HTTP(S) URL, false otherwise
   */
  private fun isValidDownloadUrl(url: String): Boolean {
    return url.startsWith("http://") || url.startsWith("https://")
  }
}

/**
 * Alternate processor that takes separate FeedRegionMap and FeedMetadataMap inputs and combines
 * them into FeedDiscoveryInput before processing.
 *
 * This is useful when the batch job has separate steps producing region and metadata maps.
 */
@Component
@StepScope
class FeedDiscoveryCombiningProcessor(private val feedDiscoveryProcessor: FeedDiscoveryProcessor) :
  ItemProcessor<Pair<FeedRegionMap, FeedMetadataMap>, FeedDiscoveryBatch> {

  private val logger = LoggerFactory.getLogger(FeedDiscoveryCombiningProcessor::class.java)

  override fun process(item: Pair<FeedRegionMap, FeedMetadataMap>): FeedDiscoveryBatch? {
    val (feedRegionMap, feedMetadataMap) = item

    logger.debug(
      "Combining feed region map ({} feeds) with feed metadata map ({} feeds)",
      feedRegionMap.size,
      feedMetadataMap.size,
    )

    val input = FeedDiscoveryInput(feedRegionMap = feedRegionMap, feedMetadataMap = feedMetadataMap)

    return feedDiscoveryProcessor.process(input)
  }
}
