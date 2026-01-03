package com.mobilispect.backend.feed.batch.import

import com.mobilispect.backend.feed.domain.model.ids.FeedId
import com.mobilispect.backend.feed.events.FeedImportFailed
import com.mobilispect.backend.feed.events.FeedImportStepCompleted
import com.mobilispect.backend.feed.events.FeedImportStepStartedEvent
import org.slf4j.LoggerFactory
import org.springframework.batch.core.ExitStatus
import org.springframework.batch.core.listener.StepExecutionListener
import org.springframework.batch.core.step.StepExecution
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Component

/**
 * Step execution listener for the feed import batch job.
 *
 * Provides observability and monitoring for each step in the feed import process by publishing
 * events, logging progress, and tracking metrics.
 *
 * Constitutional Requirement: Observability & Operational Insight
 * - Traces step execution lifecycle for operational monitoring
 * - Publishes events for downstream observability systems
 * - Logs structured information for debugging and audit trails
 */
@Component
class FeedImportStepExecutionListener(private val eventPublisher: ApplicationEventPublisher) :
  StepExecutionListener {

  private val logger = LoggerFactory.getLogger(FeedImportStepExecutionListener::class.java)

  override fun beforeStep(stepExecution: StepExecution) {
    val stepName = stepExecution.stepName
    val feedId = extractFeedId(stepExecution)

    logger.info("Starting feed import step: {} for feed: {}", stepName, feedId?.value ?: "unknown")

    feedId?.let { id ->
      eventPublisher.publishEvent(FeedImportStepStartedEvent(id, stepName))
      logger.debug("Published FeedStepStarted event for feed: {}, step: {}", id.value, stepName)
    }
  }

  override fun afterStep(stepExecution: StepExecution): ExitStatus {
    val stepName = stepExecution.stepName
    val feedId = extractFeedId(stepExecution)
    val exitStatus = stepExecution.exitStatus

    val readCount = stepExecution.readCount
    val writeCount = stepExecution.writeCount
    val skipCount = stepExecution.skipCount
    val commitCount = stepExecution.commitCount
    val rollbackCount = stepExecution.rollbackCount

    logger.info(
      "Completed feed import step: {} for feed: {} with status: {} " +
        "(read: {}, written: {}, skipped: {}, commits: {}, rollbacks: {})",
      stepName,
      feedId?.value ?: "unknown",
      exitStatus.exitCode,
      readCount,
      writeCount,
      skipCount,
      commitCount,
      rollbackCount,
    )

    feedId?.let { id ->
      when {
        exitStatus.exitCode == ExitStatus.COMPLETED.exitCode -> {
          eventPublisher.publishEvent(FeedImportStepCompleted(id, stepExecution.stepName))
          logger.debug(
            "Published FeedStepCompleted event for feed: {}, step: {}",
            id.value,
            stepExecution.stepName,
          )
        }
        exitStatus.exitCode == ExitStatus.FAILED.exitCode -> {
          val errorMessage = exitStatus.exitDescription ?: "Step failed without description"
          eventPublisher.publishEvent(FeedImportFailed(id, stepExecution.stepName, errorMessage))
          logger.error(
            "Published FeedImportFailed event for feed: {}, step: {}, error: {}",
            id.value,
            stepExecution.stepName,
            errorMessage,
          )
        }
        else -> {
          logger.warn(
            "Step completed with unexpected status: {} for feed: {}, step: {}",
            exitStatus.exitCode,
            id.value,
            stepExecution.stepName,
          )
        }
      }
    }

    return exitStatus
  }

  /**
   * Extract FeedId from step execution context or job parameters.
   *
   * The feedId can be stored in either the job execution context (set by GTFSFeedReader) or passed
   * as a job parameter (feedOnestopId).
   */
  private fun extractFeedId(stepExecution: StepExecution): FeedId? {
    // Try to get from job execution context first (set by GTFSFeedReader)
    val feedIdFromContext = stepExecution.jobExecution.executionContext.get("feedId") as? String

    // Fall back to job parameters
    val feedOnestopId = stepExecution.jobParameters.getString("feedOnestopId")

    return when {
      feedIdFromContext != null -> FeedId(feedIdFromContext)
      feedOnestopId != null -> FeedId.from(feedOnestopId)
      else -> {
        logger.warn("Could not extract feedId from step execution: {}", stepExecution.stepName)
        null
      }
    }
  }
}
