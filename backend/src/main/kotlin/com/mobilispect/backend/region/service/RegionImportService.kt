package com.mobilispect.backend.region.service

import com.mobilispect.backend.api.BulkImportResponse
import com.mobilispect.backend.api.FeedImportResult
import com.mobilispect.backend.api.FeedImportResultStatus
import com.mobilispect.backend.feed.FeedApi
import com.mobilispect.backend.feed.model.ImportTriggerType
import com.mobilispect.backend.feed.service.RateLimitedJobLauncher
import com.mobilispect.backend.region.RegionId
import com.mobilispect.backend.region.data.repository.RegionImportRepository
import com.mobilispect.backend.region.domain.RegionImport
import com.mobilispect.backend.region.domain.RegionImportId
import com.mobilispect.backend.region.domain.RegionImportStatus
import org.slf4j.LoggerFactory
import org.springframework.batch.core.job.Job
import org.springframework.batch.core.job.parameters.JobParametersBuilder
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.core.task.TaskExecutor
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import org.springframework.transaction.support.TransactionSynchronization
import org.springframework.transaction.support.TransactionSynchronizationManager

/**
 * Service responsible for managing region-level feed import operations.
 *
 * Handles bulk import operations for all feeds within a region using Spring Batch parent-child job
 * architecture with database persistence. The service:
 * - Creates a RegionImport entity persisted to database
 * - Launches regionImportJob asynchronously
 * - Tracks progress via database updates (not in-memory state)
 *
 * Constitutional Requirements:
 * - Module Boundaries: Coordinates between region and feed modules via public APIs
 * - Event-Driven Architecture: Publishes region import lifecycle events
 * - Continue-on-Failure: Ensures partial failures don't block entire region imports
 * - Database Persistence: All state tracked in database for reliability
 */
@Service
class RegionImportService(
  private val feedApi: FeedApi,
  private val regionImportRepository: RegionImportRepository,
  private val rateLimitedJobLauncher: RateLimitedJobLauncher,
  @Qualifier("regionImportJob") private val regionImportJob: Job,
  @Qualifier("taskExecutor") private val importLaunchExecutor: TaskExecutor,
) {
  private val logger = LoggerFactory.getLogger(RegionImportService::class.java)

  /**
   * Start bulk import for all ACTIVE feeds in a region.
   *
   * Creates a RegionImport entity, persists it to the database, and launches the parent
   * regionImportJob asynchronously. The job orchestrates child feed imports.
   *
   * @param regionId The region identifier
   * @param triggerType How the import was triggered (MANUAL, SCHEDULED, etc.)
   * @return Summary of the bulk import operation with parent import ID
   */
  @Transactional
  fun import(regionId: RegionId, triggerType: ImportTriggerType): BulkImportResponse {
    logger.info("Starting bulk import for region: {}", regionId.value)

    // Check for existing active import
    val activeStatuses = listOf(RegionImportStatus.PENDING, RegionImportStatus.RUNNING)
    val existingImport =
      regionImportRepository.findActiveByRegionOnestopId(regionId.value, activeStatuses)
    if (existingImport.isPresent) {
      logger.info(
        "Region import already active for region {}: {}",
        regionId.value,
        existingImport.get().id,
      )
      return buildResponseFromExistingImport(existingImport.get())
    }

    // Get active feeds to determine total count
    val feeds = feedApi.findActiveFeedsByRegion(regionId)
    if (feeds.isEmpty()) {
      logger.warn("No active feeds found for region: {}", regionId.value)
      return BulkImportResponse(
        regionImportId = null,
        regionOnestopId = regionId.value,
        status = RegionImportStatus.COMPLETED,
        totalFeeds = 0,
        startedCount = 0,
        completedCount = 0,
        failedCount = 0,
        skippedCount = 0,
        results = emptyList(),
        startedAt = null,
      )
    }

    // Create and persist the region import entity
    val regionImport =
      try {
        regionImportRepository.save(
          RegionImport(
            id = RegionImportId.random(),
            regionOnestopId = regionId.value,
            triggerType = triggerType,
            status = RegionImportStatus.PENDING,
            totalFeeds = feeds.size,
          )
        )
      } catch (e: DataIntegrityViolationException) {
        // Database constraint prevented duplicate - fetch and return the existing import
        logger.info(
          "Region import already started for region {} (caught by database constraint)",
          regionId.value,
        )
        val existing =
          regionImportRepository
            .findActiveByRegionOnestopId(regionId.value, activeStatuses)
            .orElseThrow {
              IllegalStateException(
                "Failed to create or find active region import for region ${regionId.value}"
              )
            }
        return buildResponseFromExistingImport(existing)
      }

    logger.info(
      "Created region import {} for region {} with {} feeds",
      regionImport.id.value,
      regionId.value,
      feeds.size,
    )

    // Capture values for async execution
    val capturedRegionImportId = regionImport.id
    val capturedRegionId = regionId

    // Launch job after transaction commits
    if (TransactionSynchronizationManager.isSynchronizationActive()) {
      TransactionSynchronizationManager.registerSynchronization(
        object : TransactionSynchronization {
          override fun afterCommit() {
            logger.info(
              "afterCommit hook: launching region import job for {}",
              capturedRegionImportId.value,
            )
            launchRegionImportJob(capturedRegionImportId, capturedRegionId, triggerType)
          }
        }
      )
    } else {
      launchRegionImportJob(capturedRegionImportId, capturedRegionId, triggerType)
    }

    // Build initial results (all feeds marked as pending)
    val results =
      feeds.map { feed ->
        FeedImportResult(
          feedOnestopId = feed.feedId.value,
          feedName = feed.name,
          status = FeedImportResultStatus.STARTED,
          message = "Import queued",
          importId = null, // Will be set when child job starts
        )
      }

    return BulkImportResponse(
      regionImportId = regionImport.id.value.toString(),
      regionOnestopId = regionId.value,
      status = RegionImportStatus.PENDING,
      totalFeeds = feeds.size,
      startedCount = 0,
      completedCount = 0,
      failedCount = 0,
      skippedCount = 0,
      results = results,
      startedAt = null,
    )
  }

  private fun launchRegionImportJob(
    regionImportId: RegionImportId,
    regionId: RegionId,
    triggerType: ImportTriggerType,
  ) {
    importLaunchExecutor.execute {
      val params =
        JobParametersBuilder()
          .addString("regionImportId", regionImportId.value.toString(), true)
          .addString("regionOnestopId", regionId.value, true)
          .addString("triggerType", triggerType.name, true)
          .addLong("timestamp", System.currentTimeMillis(), true)
          .toJobParameters()

      logger.info(
        "Launching region import job for region {} with import {}",
        regionId.value,
        regionImportId.value,
      )

      runCatching { rateLimitedJobLauncher.run(regionImportJob, params) }
        .onFailure { throwable ->
          logger.error(
            "Failed to launch region import job for region {}",
            regionId.value,
            throwable,
          )
          failRegionImport(regionImportId, throwable.message ?: "Failed to start import job")
        }
    }
  }

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  fun failRegionImport(regionImportId: RegionImportId, message: String) {
    val regionImport =
      regionImportRepository.findByImportId(regionImportId).orElseThrow {
        IllegalArgumentException("Region import not found: $regionImportId")
      }

    regionImport.fail(message)
    regionImportRepository.save(regionImport)
  }

  private fun buildResponseFromExistingImport(regionImport: RegionImport): BulkImportResponse {
    return BulkImportResponse(
      regionImportId = regionImport.id.value.toString(),
      regionOnestopId = regionImport.regionOnestopId,
      status = regionImport.status,
      totalFeeds = regionImport.totalFeeds,
      startedCount = regionImport.startedCount,
      completedCount = regionImport.completedCount,
      failedCount = regionImport.failedCount,
      skippedCount = regionImport.skippedCount,
      results = emptyList(), // TODO: Could load from junction table
      startedAt = regionImport.startedAt,
    )
  }

  // ========== Query Methods ==========

  /**
   * Get a region import by its ID.
   *
   * @param regionImportId The region import identifier
   * @return The region import if found, null otherwise
   */
  fun getRegionImport(regionImportId: RegionImportId): RegionImport? {
    return regionImportRepository.findByImportId(regionImportId).orElse(null)
  }

  /**
   * Get the active region import for a specific region (if any).
   *
   * @param regionId The region identifier
   * @return The active region import if one exists, null otherwise
   */
  fun getActiveImportForRegion(regionId: RegionId): RegionImport? {
    val activeStatuses = listOf(RegionImportStatus.PENDING, RegionImportStatus.RUNNING)
    return regionImportRepository
      .findActiveByRegionOnestopId(regionId.value, activeStatuses)
      .orElse(null)
  }

  /**
   * Get all active region imports (pending or running).
   *
   * @return List of active region imports
   */
  fun getActiveRegionImports(): List<RegionImport> {
    return regionImportRepository.findAllByStatusInOrderByCreatedAtAsc(
      listOf(RegionImportStatus.PENDING, RegionImportStatus.RUNNING)
    )
  }
}
