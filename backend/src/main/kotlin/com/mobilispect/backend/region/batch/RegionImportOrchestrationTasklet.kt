package com.mobilispect.backend.region.batch

import com.mobilispect.backend.feed.FeedApi
import com.mobilispect.backend.feed.domain.FeedImport
import com.mobilispect.backend.feed.model.ImportStatus
import com.mobilispect.backend.feed.model.ImportTriggerType
import com.mobilispect.backend.region.RegionId
import com.mobilispect.backend.region.data.repository.RegionImportRepository
import com.mobilispect.backend.region.domain.RegionImport
import com.mobilispect.backend.region.domain.RegionImportId
import org.slf4j.LoggerFactory
import org.springframework.batch.core.ExitStatus
import org.springframework.batch.core.configuration.annotation.StepScope
import org.springframework.batch.core.scope.context.ChunkContext
import org.springframework.batch.core.step.StepContribution
import org.springframework.batch.core.step.tasklet.Tasklet
import org.springframework.batch.infrastructure.repeat.RepeatStatus
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional

/**
 * Tasklet that orchestrates child feed import jobs for a region import.
 *
 * This tasklet:
 * 1. Updates the parent region import status to RUNNING
 * 2. Launches child feed import jobs for each active feed in the region
 * 3. Links children to parent via RegionImportFeed records
 * 4. Polls JobExplorer waiting for all children to complete
 * 5. Determines final status (COMPLETED/PARTIAL_SUCCESS/FAILED)
 *
 * Constitutional Requirements:
 * - Continue-on-failure: If some feeds fail, others continue and parent completes with
 *   PARTIAL_SUCCESS
 * - Observability: Logs detailed progress and updates database state for monitoring
 */
@Component
@StepScope
class RegionImportOrchestrationTasklet(
  private val feedApi: FeedApi,
  private val regionImportRepository: RegionImportRepository,
  @Value("#{jobParameters['regionImportId']}") private val regionImportIdStr: String? = null,
  @Value("#{jobParameters['regionOnestopId']}") private val regionOnestopId: String? = null,
  @Value("#{jobParameters['triggerType']}") private val triggerTypeStr: String? = null,
) : Tasklet {

  private val logger = LoggerFactory.getLogger(RegionImportOrchestrationTasklet::class.java)

  // Polling configuration
  private val pollIntervalMs = 5000L // 5 seconds
  private val maxPollAttempts = 720 // 1 hour max (720 * 5s = 3600s)

  override fun execute(contribution: StepContribution, chunkContext: ChunkContext): RepeatStatus {
    val regionImportId =
      regionImportIdStr?.let { RegionImportId.fromString(it) }
        ?: throw IllegalStateException("regionImportId job parameter is required")

    val regionId =
      regionOnestopId?.let { RegionId(it) }
        ?: throw IllegalStateException("regionOnestopId job parameter is required")

    val triggerType =
      triggerTypeStr?.let { ImportTriggerType.valueOf(it) } ?: ImportTriggerType.MANUAL

    logger.info(
      "Starting region import orchestration for region: {}, import: {}",
      regionId.value,
      regionImportId.value,
    )

    // Step 1: Mark region import as running
    val regionImport = markAsRunning(regionImportId, chunkContext)

    // Step 2: Get all active feeds for the region
    val feeds = feedApi.findActiveFeedsByRegion(regionId)
    if (feeds.isEmpty()) {
      logger.warn("No active feeds found for region: {}", regionId.value)
      completeRegionImport(regionImportId, completedCount = 0, failedCount = 0)
      return RepeatStatus.FINISHED
    }

    // Update total feeds count
    updateTotalFeeds(regionImportId, feeds.size)
    logger.info("Found {} active feeds for region: {}", feeds.size, regionId.value)

    // Step 3: Launch child imports for each feed
    val childImports = mutableListOf<ChildImportInfo>()
    var sequenceNumber = 0

    feeds.forEach { feed ->
      try {
        val feedImport = feedApi.import(feed.feedId, triggerType)
        logger.info("Launched import {} for feed {}", feedImport.id, feed.feedId)

        // Link child to parent
        linkChildToParent(regionImportId, feedImport, sequenceNumber++)
        incrementStartedCount(regionImportId)

        childImports.add(
          ChildImportInfo(
            feedOnestopId = feed.feedId.value,
            feedImportId = feedImport.id.value,
            feedName = feed.name,
          )
        )
      } catch (e: Exception) {
        logger.error("Failed to launch import for feed {}: {}", feed.feedId, e.message)
        // Mark as skipped (could not even start)
        incrementSkippedCount(regionImportId)
      }
    }

    if (childImports.isEmpty()) {
      logger.warn("No feed imports were started for region: {}", regionId.value)
      completeRegionImport(regionImportId, completedCount = 0, failedCount = 0)
      contribution.exitStatus = ExitStatus.FAILED
      return RepeatStatus.FINISHED
    }

    // Step 4: Poll for completion of all child imports
    logger.info("Waiting for {} child imports to complete", childImports.size)
    val results = pollForCompletion(childImports)

    // Step 5: Determine final status
    val completedCount = results.count { it.status == ImportStatus.COMPLETED }
    val failedCount = results.count { it.status == ImportStatus.FAILED }

    logger.info(
      "Region import {} complete: {} completed, {} failed out of {} total",
      regionImportId.value,
      completedCount,
      failedCount,
      childImports.size,
    )

    // Update region import with final counts
    completeRegionImport(regionImportId, completedCount, failedCount)

    // Set exit status based on results
    contribution.exitStatus =
      when {
        failedCount == 0 -> ExitStatus.COMPLETED
        completedCount > 0 -> ExitStatus("PARTIAL_SUCCESS")
        else -> ExitStatus.FAILED
      }

    return RepeatStatus.FINISHED
  }

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  fun markAsRunning(regionImportId: RegionImportId, chunkContext: ChunkContext): RegionImport {
    val regionImport =
      regionImportRepository.findByImportId(regionImportId).orElseThrow {
        IllegalStateException("Region import not found: $regionImportId")
      }

    val jobExecutionId = chunkContext.stepContext.stepExecution.jobExecutionId
    regionImport.start(jobExecutionId)
    return regionImportRepository.save(regionImport)
  }

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  fun updateTotalFeeds(regionImportId: RegionImportId, totalFeeds: Int) {
    val regionImport =
      regionImportRepository.findByImportId(regionImportId).orElseThrow {
        IllegalStateException("Region import not found: $regionImportId")
      }

    regionImport.totalFeeds = totalFeeds
    regionImportRepository.save(regionImport)
  }

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  fun linkChildToParent(
    regionImportId: RegionImportId,
    feedImport: FeedImport,
    sequenceNumber: Int,
  ) {
    val regionImport =
      regionImportRepository.findByImportId(regionImportId).orElseThrow {
        IllegalStateException("Region import not found: $regionImportId")
      }

    regionImport.addFeed(feedImport.id.value, sequenceNumber)
    regionImportRepository.save(regionImport)
  }

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  fun incrementStartedCount(regionImportId: RegionImportId) {
    val regionImport =
      regionImportRepository.findByImportId(regionImportId).orElseThrow {
        IllegalStateException("Region import not found: $regionImportId")
      }

    regionImport.markFeedStarted()
    regionImportRepository.save(regionImport)
  }

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  fun incrementSkippedCount(regionImportId: RegionImportId) {
    val regionImport =
      regionImportRepository.findByImportId(regionImportId).orElseThrow {
        IllegalStateException("Region import not found: $regionImportId")
      }

    regionImport.markFeedSkipped()
    regionImportRepository.save(regionImport)
  }

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  fun completeRegionImport(regionImportId: RegionImportId, completedCount: Int, failedCount: Int) {
    val regionImport =
      regionImportRepository.findByImportId(regionImportId).orElseThrow {
        IllegalStateException("Region import not found: $regionImportId")
      }

    // Update counts
    regionImport.completedCount = completedCount
    regionImport.failedCount = failedCount

    // Finalize will set the appropriate terminal status
    regionImport.finalize()
    regionImportRepository.save(regionImport)

    logger.info(
      "Region import {} finalized with status: {}",
      regionImportId.value,
      regionImport.status,
    )
  }

  /**
   * Poll for completion of all child feed imports. Returns when all imports have reached a terminal
   * state (completed, failed, or cancelled).
   */
  private fun pollForCompletion(childImports: List<ChildImportInfo>): List<ChildImportResult> {
    val feedImportIds = childImports.map { it.feedImportId }.toSet()
    var attempts = 0

    while (attempts < maxPollAttempts) {
      attempts++

      // Check status of all child imports
      val results = checkChildImportStatuses(childImports)
      val pendingCount = results.count { !it.status.isTerminal() }

      if (pendingCount == 0) {
        logger.info("All {} child imports have completed after {} polls", results.size, attempts)
        return results
      }

      logger.debug(
        "Poll {}: {} of {} child imports still pending",
        attempts,
        pendingCount,
        results.size,
      )

      try {
        Thread.sleep(pollIntervalMs)
      } catch (e: InterruptedException) {
        Thread.currentThread().interrupt()
        throw RuntimeException("Polling interrupted", e)
      }
    }

    logger.warn("Polling timed out after {} attempts", maxPollAttempts)
    // Return current status even if not all complete
    return checkChildImportStatuses(childImports)
  }

  /** Check the status of child feed imports by querying the FeedApi. */
  private fun checkChildImportStatuses(
    childImports: List<ChildImportInfo>
  ): List<ChildImportResult> {
    return childImports.map { childInfo ->
      val status =
        feedApi.getImportStatus(childInfo.feedImportId)
          ?: ImportStatus.FAILED // Assume failed if not found

      ChildImportResult(
        feedOnestopId = childInfo.feedOnestopId,
        feedImportId = childInfo.feedImportId,
        feedName = childInfo.feedName,
        status = status,
      )
    }
  }

  /** Returns true if the import status is terminal (no more changes expected). */
  private fun ImportStatus.isTerminal(): Boolean =
    this in setOf(ImportStatus.COMPLETED, ImportStatus.FAILED, ImportStatus.CANCELLED)

  private data class ChildImportInfo(
    val feedOnestopId: String,
    val feedImportId: java.util.UUID,
    val feedName: String,
  )

  private data class ChildImportResult(
    val feedOnestopId: String,
    val feedImportId: java.util.UUID,
    val feedName: String,
    val status: ImportStatus,
  )
}
