package com.mobilispect.backend.feed.service

import com.mobilispect.backend.feed.model.ImportTriggerType
import com.mobilispect.backend.feed.repository.MetropolitanRegionRepository
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service

/**
 * Scheduler for automated daily feed update checks.
 *
 * Task T041: Create FeedUpdateScheduler for daily checks Per FR-013: System MUST check all
 * configured regions daily for feed updates
 *
 * This service runs on a scheduled basis (daily at 2 AM by default) to:
 * 1. Identify all regions with automatic updates enabled
 * 2. Check each region's feeds for new versions using SHA-1 hash comparison
 * 3. Trigger automatic imports when updates are detected
 *
 * The scheduler uses FeedVersionService for SHA-1 hash comparison and FeedImportService for
 * triggering automatic imports when changes are detected.
 */
@Service
class FeedUpdateScheduler(
  private val regionRepository: MetropolitanRegionRepository,
  private val feedVersionService: FeedVersionService,
  private val feedImportService: FeedImportService,
) {
  private val logger = LoggerFactory.getLogger(FeedUpdateScheduler::class.java)

  /**
   * Check all regions with automatic updates enabled for feed updates. Runs daily at 2:00 AM
   * (configurable via cron expression).
   *
   * Cron: "0 0 2 * * *" = second=0, minute=0, hour=2, every day
   */
  @Scheduled(cron = "\${feed-management.scheduler.daily-check-cron:0 0 2 * * *}")
  fun performDailyFeedCheck() {
    logger.info("Starting daily feed update check")

    val regionsWithAutoUpdate = regionRepository.findAllByAutoUpdateEnabled(true)
    logger.info("Found {} regions with automatic updates enabled", regionsWithAutoUpdate.size)

    var totalChecked = 0
    var totalUpdatesFound = 0
    var totalImportsTriggered = 0
    var totalErrors = 0

    regionsWithAutoUpdate.forEach { region ->
      try {
        logger.debug("Checking feeds for region: {}", region.name)
        val result = feedVersionService.checkForUpdates(region.regionOnestopId)

        totalChecked += result.feedsChecked
        totalUpdatesFound += result.updatesDetected

        // Trigger automatic imports for feeds with updates
        result.feedsWithUpdates.forEach { feedOnestopId ->
          try {
            feedImportService.startImport(
              feedId = feedOnestopId,
              // System-triggered
              triggerType = ImportTriggerType.AUTOMATIC,
            )
            totalImportsTriggered++
            logger.info("Triggered automatic import for feed: {}", feedOnestopId)
          } catch (ex: Exception) {
            logger.error("Failed to trigger automatic import for feed: {}", feedOnestopId, ex)
            totalErrors++
          }
        }
      } catch (ex: Exception) {
        logger.error("Error checking feeds for region: {}", region.name, ex)
        totalErrors++
      }
    }

    logger.info(
      "Daily feed check completed. Regions: {}, Feeds checked: {}, Updates found: {}, " +
        "Imports triggered: {}, Errors: {}",
      regionsWithAutoUpdate.size,
      totalChecked,
      totalUpdatesFound,
      totalImportsTriggered,
      totalErrors,
    )
  }

  /** Health check that runs every 6 hours to verify scheduler is active. */
  @Scheduled(fixedRate = 21600000) // 6 hours in milliseconds
  fun schedulerHealthCheck() {
    logger.debug("Feed update scheduler health check - active")
  }
}
