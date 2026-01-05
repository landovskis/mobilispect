package com.mobilispect.backend.region.service

import com.mobilispect.backend.api.BulkImportResponse
import com.mobilispect.backend.api.FeedImportResult
import com.mobilispect.backend.api.FeedImportResultStatus
import com.mobilispect.backend.feed.domain.model.ids.FeedId
import com.mobilispect.backend.feed.model.FeedStatus
import com.mobilispect.backend.feed.model.ImportStatus
import com.mobilispect.backend.feed.model.ImportTriggerType
import com.mobilispect.backend.feed.model.ids.RegionId
import com.mobilispect.backend.feed.repository.FeedImportRepository
import com.mobilispect.backend.feed.repository.FeedRepository
import com.mobilispect.backend.feed.service.FeedImportService
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.context.ApplicationEventPublisher
import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * Service responsible for managing region-level feed import operations.
 *
 * Handles bulk import operations for all feeds within a region, coordinating with FeedImportService
 * for individual feed imports while managing region-level lifecycle events and aggregating results.
 *
 * Constitutional Requirements:
 * - Module Boundaries: Coordinates between region and feed modules via public APIs
 * - Event-Driven Architecture: Publishes region import lifecycle events
 * - Continue-on-Failure: Ensures partial failures don't block entire region imports
 */
@Service
class RegionImportService(
  @Qualifier("feedManagementFeedRepository") private val feedRepository: FeedRepository,
  private val feedImportRepository: FeedImportRepository,
  private val feedImportService: FeedImportService,
  private val eventPublisher: ApplicationEventPublisher,
) {
  private val logger = LoggerFactory.getLogger(RegionImportService::class.java)

  /**
   * Start bulk import for all ACTIVE feeds in a region.
   *
   * Continues on failure - if one feed fails to import, the operation continues with remaining
   * feeds. Automatically skips feeds that already have an active import running.
   *
   * @param regionId The region identifier
   * @param triggerType How the import was triggered (MANUAL, SCHEDULED, etc.)
   * @return Summary of the bulk import operation with per-feed results
   */
  @Transactional
  fun startBulkImportForRegion(
    regionId: RegionId,
    triggerType: ImportTriggerType,
  ): BulkImportResponse {
    logger.info("Starting bulk import for region: $regionId")

    // Fetch all ACTIVE feeds for the region
    val feeds =
      feedRepository.findAllByRegionRegionOnestopIdAndStatusIn(regionId, listOf(FeedStatus.ACTIVE))

    if (feeds.isEmpty()) {
      logger.warn("No active feeds found for region: $regionId")
      return BulkImportResponse(
        regionOnestopId = regionId.value,
        totalFeeds = 0,
        startedCount = 0,
        failedCount = 0,
        skippedCount = 0,
        results = emptyList(),
      )
    }

    val results = mutableListOf<FeedImportResult>()
    var startedCount = 0
    var failedCount = 0
    var skippedCount = 0

    eventPublisher.publishEvent(RegionFeedsImportStartedEvent(regionId))
    // Process each feed with continue-on-failure semantics
    feeds.forEach { feed ->
      try {
        // Check if feed already has an active import
        val hasActiveImport =
          feedImportRepository
            .findAllByFeedIdAndStatusInOrderByStartedAtDesc(
              feed.feedId,
              listOf(ImportStatus.PENDING, ImportStatus.RUNNING),
              PageRequest.of(0, 1),
            )
            .content
            .isNotEmpty()

        if (hasActiveImport) {
          results.add(
            FeedImportResult(
              feedOnestopId = feed.feedId,
              feedName = feed.name,
              status = FeedImportResultStatus.SKIPPED,
              message = "Import already running",
            )
          )
          skippedCount++
          return@forEach
        }

        // Start import for this feed
        val feedImport = feedImportService.startImport(FeedId(feed.feedId), triggerType)
        logger.debug("Started import for feed ${feed.feedId}: ${feedImport.id}")
        results.add(
          FeedImportResult(
            feedOnestopId = feed.feedId,
            feedName = feed.name,
            status = FeedImportResultStatus.STARTED,
            message = "Import started successfully",
            importId = feedImport.id.value.toString(),
          )
        )
        startedCount++
      } catch (e: Exception) {
        logger.error("Failed to start import for feed ${feed.feedId}", e)
        results.add(
          FeedImportResult(
            feedOnestopId = feed.feedId,
            feedName = feed.name,
            status = FeedImportResultStatus.FAILED,
            message = e.message ?: "Unknown error",
          )
        )
        failedCount++
      }
    }

    logger.info(
      "Bulk import for region $regionId completed: $startedCount started, $failedCount failed, $skippedCount skipped"
    )

    eventPublisher.publishEvent(RegionFeedsImportCompletedEvent(regionId))
    return BulkImportResponse(
      regionOnestopId = regionId.value,
      totalFeeds = feeds.size,
      startedCount = startedCount,
      failedCount = failedCount,
      skippedCount = skippedCount,
      results = results,
    )
  }
}
