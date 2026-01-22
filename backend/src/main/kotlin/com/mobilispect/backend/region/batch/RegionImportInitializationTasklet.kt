package com.mobilispect.backend.region.batch

import com.mobilispect.backend.feed.FeedApi
import com.mobilispect.backend.region.RegionId
import com.mobilispect.backend.region.data.repository.RegionImportRepository
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
 * Initialization tasklet that prepares the region import for parallel processing.
 *
 * This tasklet runs before the partitioned feed import step and:
 * 1. Marks the region import as RUNNING
 * 2. Counts and records the total number of active feeds
 * 3. Stores the job execution ID for tracking
 *
 * If no active feeds are found, the import is marked as COMPLETED immediately and the job exits
 * early with a special exit status.
 *
 * Constitutional Requirements:
 * - Observability: Logs initialization progress
 * - Module boundaries: Uses FeedApi for feed count, not direct repository access
 */
@Component
@StepScope
class RegionImportInitializationTasklet(
  private val feedApi: FeedApi,
  private val regionImportRepository: RegionImportRepository,
  @Value("#{jobParameters['regionImportId']}") private val regionImportIdStr: String? = null,
  @Value("#{jobParameters['regionOnestopId']}") private val regionOnestopId: String? = null,
) : Tasklet {

  private val logger = LoggerFactory.getLogger(RegionImportInitializationTasklet::class.java)

  override fun execute(contribution: StepContribution, chunkContext: ChunkContext): RepeatStatus {
    val regionImportId =
      regionImportIdStr?.let { RegionImportId.fromString(it) }
        ?: throw IllegalStateException("regionImportId job parameter is required")

    val regionId =
      regionOnestopId?.let { RegionId(it) }
        ?: throw IllegalStateException("regionOnestopId job parameter is required")

    logger.info("Initializing region import {} for region {}", regionImportId.value, regionId.value)

    // Mark as running and get feed count
    val feedCount = initializeImport(regionImportId, chunkContext)

    if (feedCount == 0) {
      logger.warn("No active feeds found for region {}, completing early", regionId.value)
      completeImportWithNoFeeds(regionImportId)
      contribution.exitStatus = ExitStatus("NO_FEEDS")
      return RepeatStatus.FINISHED
    }

    // Get active feeds and count them
    val feeds = feedApi.findActiveFeedsByRegion(regionId)
    updateTotalFeeds(regionImportId, feeds.size)

    logger.info(
      "Region import {} initialized with {} active feeds",
      regionImportId.value,
      feeds.size,
    )

    contribution.exitStatus = ExitStatus.COMPLETED
    return RepeatStatus.FINISHED
  }

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  fun initializeImport(regionImportId: RegionImportId, chunkContext: ChunkContext): Int {
    val regionImport =
      regionImportRepository.findByImportId(regionImportId).orElseThrow {
        IllegalStateException("Region import not found: $regionImportId")
      }

    val jobExecutionId = chunkContext.stepContext.stepExecution.jobExecutionId
    regionImport.start(jobExecutionId)
    regionImportRepository.save(regionImport)

    // Get feed count from FeedApi
    val regionId = RegionId(regionImport.regionOnestopId)
    val feeds = feedApi.findActiveFeedsByRegion(regionId)
    return feeds.size
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
  fun completeImportWithNoFeeds(regionImportId: RegionImportId) {
    val regionImport =
      regionImportRepository.findByImportId(regionImportId).orElseThrow {
        IllegalStateException("Region import not found: $regionImportId")
      }
    regionImport.totalFeeds = 0
    regionImport.finalize()
    regionImportRepository.save(regionImport)
  }
}
