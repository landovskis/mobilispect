package com.mobilispect.backend.feed.service

import com.mobilispect.backend.agency.batch.import.AgencyImportService
import com.mobilispect.backend.feed.api.GTFSData
import com.mobilispect.backend.feed.domain.FeedImport
import com.mobilispect.backend.feed.domain.model.ids.FeedId
import com.mobilispect.backend.feed.model.FeedStatus
import com.mobilispect.backend.feed.model.ImportStatus
import com.mobilispect.backend.feed.model.ImportTriggerType
import com.mobilispect.backend.feed.repository.FeedImportRepository
import com.mobilispect.backend.feed.repository.FeedRepository
import com.mobilispect.backend.route.batch.frequency.FrequencyImportService
import com.mobilispect.backend.route.batch.import.RouteImportService
import com.mobilispect.backend.route.batch.spacing.StopSpacingImportService
import com.mobilispect.backend.route.batch.variant.RouteVariantImportService
import java.time.Clock
import java.time.LocalDate
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.context.annotation.Lazy
import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional

/**
 * Synchronous feed import service that processes a feed without launching a Spring Batch job.
 *
 * This service is designed for parallel execution within a single region import job, allowing
 * multiple feeds to be processed concurrently. Unlike [FeedImportService], which launches
 * asynchronous batch jobs, this service:
 * 1. Creates the FeedImport record
 * 2. Downloads and parses the GTFS feed
 * 3. Processes all entities (agencies, routes, variants, stop spacings, frequencies)
 * 4. Updates the FeedImport status to COMPLETED or FAILED
 *
 * This service reuses the same processing logic as the Spring Batch job by delegating to the
 * existing import services (AgencyImportService, RouteImportService, etc.).
 *
 * Constitutional Requirements:
 * - Module boundaries: Uses existing import services for cross-module processing
 * - Test-driven quality: Processing logic shared with batch job for consistency
 * - Observability: Structured logging throughout
 */
@Service
class FeedImportSyncService(
  @Qualifier("feedManagementFeedRepository") private val feedRepository: FeedRepository,
  private val feedImportRepository: FeedImportRepository,
  private val gtfsFeedDownloader: GTFSFeedDownloader,
  @Lazy private val agencyImportService: AgencyImportService,
  @Lazy private val routeImportService: RouteImportService,
  @Lazy private val routeVariantImportService: RouteVariantImportService,
  @Lazy private val stopSpacingImportService: StopSpacingImportService,
  @Lazy private val frequencyImportService: FrequencyImportService,
  private val clock: Clock = Clock.systemUTC(),
) {
  private val logger = LoggerFactory.getLogger(FeedImportSyncService::class.java)

  /**
   * Import a feed synchronously.
   *
   * Creates the FeedImport record, downloads/parses the GTFS feed, processes all entities, and
   * returns the completed import record.
   *
   * @param feedId The feed to import
   * @param triggerType How the import was triggered
   * @return The FeedImport with final status (COMPLETED or FAILED)
   */
  @Transactional(propagation = Propagation.REQUIRES_NEW)
  fun importSync(feedId: FeedId, triggerType: ImportTriggerType): FeedImport {
    logger.info("Starting synchronous import for feed: {}", feedId.value)

    // Check for existing active imports
    val activeImport =
      feedImportRepository
        .findAllByFeedIdAndStatusInOrderByStartedAtDesc(
          feedId.value,
          listOf(ImportStatus.PENDING, ImportStatus.RUNNING),
          PageRequest.of(0, 1),
        )
        .content
        .firstOrNull()

    if (activeImport != null) {
      val now = clock.instant()
      val importAge = java.time.Duration.between(activeImport.startedAt, now)
      if (importAge.toHours() >= 1) {
        logger.warn(
          "Found stale import {} for feed {} (running for {} hours), marking as failed",
          activeImport.id,
          feedId,
          importAge.toHours(),
        )
        activeImport.status = ImportStatus.FAILED
        activeImport.completedAt = now
        activeImport.errorMessage = "Import timed out - stuck in running state for > 1 hour"
        feedImportRepository.save(activeImport)
      } else {
        logger.info(
          "Import already running for feed {}, returning existing import {}",
          feedId,
          activeImport.id,
        )
        return activeImport
      }
    }

    // Create the feed import record
    val feed =
      feedRepository.findByFeedOnestopId(feedId.value).orElseThrow {
        IllegalArgumentException("Feed not found: $feedId")
      }

    val now = clock.instant()
    val feedImport =
      feedImportRepository.save(
        FeedImport().apply {
          this.feedId = feed.feedId
          this.administrator = null
          this.triggerType = triggerType
          this.status = ImportStatus.RUNNING
          this.startedAt = now
          this.versionSha1 = null
        }
      )

    logger.info("Created FeedImport {} for feed {}", feedImport.id, feedId)

    return try {
      // Download and parse GTFS
      val parseResult = gtfsFeedDownloader.downloadAndParse(feedId)

      parseResult
        .onSuccess { parsedData ->
          processGtfsData(feedId, parsedData)
          completeFeedImport(feedImport, feed.feedId)
        }
        .onFailure { error ->
          logger.error("Failed to parse GTFS for feed {}: {}", feedId, error.message)
          failFeedImport(feedImport, error.message ?: "Parse failed")
        }

      // Refresh and return the import
      feedImportRepository.findByImportId(feedImport.id).orElse(feedImport)
    } catch (e: Exception) {
      logger.error("Unexpected error during import of feed {}", feedId, e)
      failFeedImport(feedImport, e.message ?: "Unexpected error")
      feedImportRepository.findByImportId(feedImport.id).orElse(feedImport)
    }
  }

  /**
   * Process parsed GTFS data using the same services as the Spring Batch job.
   *
   * This ensures consistency between synchronous and batch processing modes.
   */
  private fun processGtfsData(feedId: FeedId, data: GTFSData) {
    logger.info(
      "Processing GTFS data for feed {}: {} agencies, {} routes, {} trips",
      feedId.value,
      data.agencies.size,
      data.routes.size,
      data.trips.size,
    )

    // Step 1: Process agencies (reuses AgencyImportService)
    val agencies = agencyImportService.processAgencies(feedId, data)
    logger.info("Processed {} agencies for feed {}", agencies.size, feedId.value)

    // Step 2: Process routes (reuses RouteImportService)
    val routes = routeImportService.processRoutes(feedId, data)
    logger.info("Processed {} routes for feed {}", routes.size, feedId.value)

    // Step 3: Process route variants (reuses RouteVariantImportService)
    val variants = routeVariantImportService.processVariants(data, routes)
    logger.info("Processed {} route variants for feed {}", variants.size, feedId.value)

    // Step 4: Process stop spacings (reuses StopSpacingImportService)
    val spacings = stopSpacingImportService.processStopSpacings(data, variants)
    logger.info("Processed {} stop spacings for feed {}", spacings.size, feedId.value)

    // Step 5: Process frequencies (reuses FrequencyImportService)
    val frequencies = frequencyImportService.processFrequencies(data, LocalDate.now(), variants)
    logger.info("Processed {} frequencies for feed {}", frequencies.size, feedId.value)
  }

  private fun completeFeedImport(feedImport: FeedImport, feedOnestopId: String) {
    val now = clock.instant()
    feedImport.status = ImportStatus.COMPLETED
    feedImport.completedAt = now
    feedImportRepository.save(feedImport)

    // Update feed status
    feedRepository.findByFeedOnestopId(feedOnestopId).ifPresent { feed ->
      feed.status = FeedStatus.ACTIVE
      feed.lastUpdatedAt = now
      feedRepository.save(feed)
    }

    logger.info("Completed import {} for feed {}", feedImport.id, feedOnestopId)
  }

  private fun failFeedImport(feedImport: FeedImport, message: String) {
    feedImport.status = ImportStatus.FAILED
    feedImport.completedAt = clock.instant()
    feedImport.errorMessage = message
    feedImportRepository.save(feedImport)

    logger.error("Failed import {} with message: {}", feedImport.id, message)
  }
}
