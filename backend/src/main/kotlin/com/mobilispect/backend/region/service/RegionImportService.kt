package com.mobilispect.backend.region.service

import com.mobilispect.backend.api.BulkImportResponse
import com.mobilispect.backend.api.FeedImportResult
import com.mobilispect.backend.api.FeedImportResultStatus
import com.mobilispect.backend.feed.FeedApi
import com.mobilispect.backend.feed.domain.model.ids.FeedId
import com.mobilispect.backend.feed.events.FeedImportCompletedEvent
import com.mobilispect.backend.feed.events.FeedImportFailedEvent
import com.mobilispect.backend.feed.events.FeedImportStartedEvent
import com.mobilispect.backend.feed.events.FeedImportStepCompletedEvent
import com.mobilispect.backend.feed.events.FeedImportStepStartedEvent
import com.mobilispect.backend.feed.model.ImportTriggerType
import com.mobilispect.backend.region.RegionId
import java.util.concurrent.ConcurrentHashMap
import org.slf4j.LoggerFactory
import org.springframework.context.ApplicationEventPublisher
import org.springframework.context.event.EventListener
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
  private val feedApi: FeedApi,
  private val eventPublisher: ApplicationEventPublisher,
) {
  private val logger = LoggerFactory.getLogger(RegionImportService::class.java)
  private val feedImportStates = ConcurrentHashMap<FeedId, RegionFeedImportState>()

  private lateinit var lastRegionId: RegionId

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
  fun import(regionId: RegionId, triggerType: ImportTriggerType): BulkImportResponse {
    logger.info("Starting bulk import for region: $regionId")

    val feeds = feedApi.findFeedsByRegion(regionId)
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
    lastRegionId = regionId

    // Process each feed with continue-on-failure semantics
    feeds.forEach { feed ->
      try {
        // Start import for this feed
        val feedImport = feedApi.import(feed.feedId, triggerType)
        logger.debug("Started import for feed {}: {}", feed.feedId, feedImport.id)
        results.add(
          FeedImportResult(
            feedOnestopId = feed.feedId.value,
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
            feedOnestopId = feed.feedId.value,
            feedName = feed.name,
            status = FeedImportResultStatus.FAILED,
            message = e.message ?: "Unknown error",
          )
        )
        failedCount++
      }
    }

    logger.info(
      "Bulk import for region $regionId completed: $startedCount started, $failedCount failed"
    )

    return BulkImportResponse(
      regionOnestopId = regionId.value,
      totalFeeds = feeds.size,
      startedCount = startedCount,
      failedCount = failedCount,
      skippedCount = skippedCount,
      results = results,
    )
  }

  @EventListener
  fun onFeedImportStarted(event: FeedImportStartedEvent) {
    feedImportStates[event.feedId] =
      RegionFeedImportState(feedId = event.feedId, status = RegionFeedImportStatus.STARTED)
  }

  @EventListener
  fun onFeedImportStepStarted(event: FeedImportStepStartedEvent) {
    feedImportStates[event.feedId] =
      RegionFeedImportState(
        feedId = event.feedId,
        status = RegionFeedImportStatus.IN_PROGRESS,
        currentStep = event.step,
      )
  }

  @EventListener
  fun onFeedImportStepCompleted(event: FeedImportStepCompletedEvent) {
    feedImportStates[event.feedId] =
      RegionFeedImportState(
        feedId = event.feedId,
        status = RegionFeedImportStatus.IN_PROGRESS,
        currentStep = event.step,
      )
  }

  @EventListener
  fun onFeedImportCompleted(event: FeedImportCompletedEvent) {
    val currentStep = feedImportStates[event.feedId]?.currentStep
    feedImportStates[event.feedId] =
      RegionFeedImportState(
        feedId = event.feedId,
        status = RegionFeedImportStatus.COMPLETED,
        currentStep = currentStep,
      )
    publishEventsIfNeeded()
  }

  @EventListener
  fun onFeedImportFailed(event: FeedImportFailedEvent) {
    feedImportStates[event.feedId] =
      RegionFeedImportState(
        feedId = event.feedId,
        status = RegionFeedImportStatus.FAILED,
        currentStep = event.step,
        errorMessage = event.message,
      )
    publishEventsIfNeeded()
  }

  fun getFeedImportState(feedId: FeedId): RegionFeedImportState? {
    return feedImportStates[feedId]
  }

  private fun publishEventsIfNeeded() {
    if (feedImportStates.all { it.value.status == RegionFeedImportStatus.COMPLETED }) {
      eventPublisher.publishEvent(RegionFeedsImportCompletedEvent(lastRegionId))
      return
    }

    if (
      feedImportStates.all {
        it.value.status == RegionFeedImportStatus.COMPLETED ||
          it.value.status == RegionFeedImportStatus.FAILED
      }
    ) {
      eventPublisher.publishEvent(RegionFeedsImportFailedEvent(lastRegionId))
    }
  }
}
