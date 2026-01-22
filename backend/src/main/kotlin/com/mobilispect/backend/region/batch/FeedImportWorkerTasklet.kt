package com.mobilispect.backend.region.batch

import com.mobilispect.backend.feed.FeedApi
import com.mobilispect.backend.feed.domain.model.ids.FeedId
import com.mobilispect.backend.feed.model.ImportStatus
import com.mobilispect.backend.feed.model.ImportTriggerType
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
 * Worker tasklet that processes a single feed import within a partitioned step.
 *
 * This tasklet is executed in parallel by multiple threads, with each partition processing a
 * different feed. It:
 * 1. Retrieves feed information from the partition's ExecutionContext
 * 2. Calls FeedApi.importSync() to process the feed synchronously
 * 3. Updates the region import tracking (completed/failed counts)
 * 4. Sets the step exit status based on import result
 *
 * The tasklet uses FeedApi to maintain module boundaries - the region module orchestrates imports
 * but delegates actual processing to the feed module.
 *
 * Constitutional Requirements:
 * - Modular monolith: Uses FeedApi interface, no direct feed repository access
 * - Continue-on-failure: Individual feed failures don't stop other partitions
 * - Observability: Structured logging for tracking progress
 */
@Component
@StepScope
class FeedImportWorkerTasklet(
  private val feedApi: FeedApi,
  private val regionImportRepository: RegionImportRepository,
  @Value("#{jobParameters['regionImportId']}") private val regionImportIdStr: String? = null,
  @Value("#{stepExecutionContext['feedOnestopId']}") private val feedOnestopId: String? = null,
  @Value("#{stepExecutionContext['feedName']}") private val feedName: String? = null,
  @Value("#{stepExecutionContext['partitionIndex']}") private val partitionIndex: Int? = null,
  @Value("#{stepExecutionContext['triggerType']}") private val triggerTypeStr: String? = null,
) : Tasklet {

  private val logger = LoggerFactory.getLogger(FeedImportWorkerTasklet::class.java)

  override fun execute(contribution: StepContribution, chunkContext: ChunkContext): RepeatStatus {
    val feedId =
      feedOnestopId?.let { FeedId(it) }
        ?: throw IllegalStateException("feedOnestopId not found in step execution context")

    val regionImportId =
      regionImportIdStr?.let { RegionImportId.fromString(it) }
        ?: throw IllegalStateException("regionImportId job parameter is required")

    val triggerType =
      triggerTypeStr?.let { ImportTriggerType.valueOf(it) } ?: ImportTriggerType.MANUAL

    logger.info(
      "Worker starting import for feed {} ({}) - partition {}",
      feedId.value,
      feedName ?: "unknown",
      partitionIndex ?: -1,
    )

    return try {
      // Process the feed synchronously
      val feedImport = feedApi.importSync(feedId, triggerType)

      // Link the feed import to the region import
      linkFeedToRegionImport(regionImportId, feedImport.id.value, partitionIndex ?: 0)

      // Update status based on result
      when (feedImport.status) {
        ImportStatus.COMPLETED -> {
          logger.info(
            "Worker completed import for feed {} - partition {}",
            feedId.value,
            partitionIndex,
          )
          markFeedCompleted(regionImportId)
          contribution.exitStatus = ExitStatus.COMPLETED
        }
        ImportStatus.FAILED -> {
          logger.warn(
            "Worker import failed for feed {} - partition {}: {}",
            feedId.value,
            partitionIndex,
            feedImport.errorMessage,
          )
          markFeedFailed(regionImportId)
          // Mark as FAILED but allow other partitions to continue
          contribution.exitStatus = ExitStatus.FAILED
        }
        else -> {
          logger.warn(
            "Worker import for feed {} ended with unexpected status: {}",
            feedId.value,
            feedImport.status,
          )
          markFeedFailed(regionImportId)
          contribution.exitStatus = ExitStatus.FAILED
        }
      }

      RepeatStatus.FINISHED
    } catch (e: Exception) {
      logger.error(
        "Worker exception during import for feed {} - partition {}",
        feedId.value,
        partitionIndex,
        e,
      )
      markFeedFailed(regionImportId)
      contribution.exitStatus = ExitStatus.FAILED
      // Re-throw to signal failure but allow partitioner to continue with other feeds
      throw e
    }
  }

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  fun linkFeedToRegionImport(
    regionImportId: RegionImportId,
    feedImportId: java.util.UUID,
    sequenceNumber: Int,
  ) {
    val regionImport =
      regionImportRepository.findByImportId(regionImportId).orElseThrow {
        IllegalStateException("Region import not found: $regionImportId")
      }
    regionImport.addFeed(feedImportId, sequenceNumber)
    regionImportRepository.save(regionImport)
  }

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  fun markFeedCompleted(regionImportId: RegionImportId) {
    val regionImport =
      regionImportRepository.findByImportId(regionImportId).orElseThrow {
        IllegalStateException("Region import not found: $regionImportId")
      }
    regionImport.markFeedCompleted()
    regionImportRepository.save(regionImport)
  }

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  fun markFeedFailed(regionImportId: RegionImportId) {
    val regionImport =
      regionImportRepository.findByImportId(regionImportId).orElseThrow {
        IllegalStateException("Region import not found: $regionImportId")
      }
    regionImport.markFeedFailed()
    regionImportRepository.save(regionImport)
  }
}
