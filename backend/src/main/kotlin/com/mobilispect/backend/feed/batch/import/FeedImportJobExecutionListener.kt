package com.mobilispect.backend.feed.batch.import

import com.mobilispect.backend.feed.domain.model.ids.FeedId
import com.mobilispect.backend.feed.events.FeedImportCompletedEvent
import com.mobilispect.backend.feed.events.FeedImportFailedEvent
import com.mobilispect.backend.feed.events.FeedImportStartedEvent
import org.slf4j.LoggerFactory
import org.springframework.batch.core.BatchStatus
import org.springframework.batch.core.job.JobExecution
import org.springframework.batch.core.listener.JobExecutionListener
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Component

/**
 * Job execution listener for the feed import batch job.
 *
 * Provides observability and monitoring for the overall feed import job lifecycle by publishing
 * events, logging progress, and tracking metrics.
 *
 * Constitutional Requirement: Observability & Operational Insight
 * - Traces job execution lifecycle for operational monitoring
 * - Publishes events for downstream observability systems
 * - Logs structured information for debugging and audit trails
 */
@Component
class FeedImportJobExecutionListener(private val eventPublisher: ApplicationEventPublisher) :
  JobExecutionListener {

  private val logger = LoggerFactory.getLogger(FeedImportJobExecutionListener::class.java)

  override fun beforeJob(jobExecution: JobExecution) {
    val feedId = extractFeedId(jobExecution)

    logger.info("Starting feed import job for feed: {}", feedId?.value ?: "unknown")

    feedId?.let { id ->
      eventPublisher.publishEvent(FeedImportStartedEvent(id))
      logger.debug("Published FeedImportStarted event for feed: {}", id.value)
    }
  }

  override fun afterJob(jobExecution: JobExecution) {
    val feedId = extractFeedId(jobExecution)
    val status = jobExecution.status
    val exitStatus = jobExecution.exitStatus

    logger.info(
      "Completed feed import job for feed: {} with status: {} (exit code: {})",
      feedId?.value ?: "unknown",
      status,
      exitStatus.exitCode,
    )

    feedId?.let { id ->
      when (status) {
        BatchStatus.COMPLETED -> {
          eventPublisher.publishEvent(FeedImportCompletedEvent(id))
          logger.info("Published FeedImportCompleted event for feed: {}", id.value)
        }
        BatchStatus.FAILED -> {
          val errorMessage = extractErrorMessage(jobExecution)
          eventPublisher.publishEvent(FeedImportFailedEvent(id, "job", errorMessage))
          logger.error(
            "Published FeedImportFailed event for feed: {}, error: {}",
            id.value,
            errorMessage,
          )
        }
        else -> {
          logger.warn("Job completed with unexpected status: {} for feed: {}", status, id.value)
        }
      }
    }
  }

  /** Extract FeedId from job execution parameters. */
  private fun extractFeedId(jobExecution: JobExecution): FeedId? {
    val feedOnestopId = jobExecution.jobParameters.getString("feedOnestopId")

    return when {
      feedOnestopId != null -> FeedId.from(feedOnestopId)
      else -> {
        logger.warn("Could not extract feedId from job execution")
        null
      }
    }
  }

  /**
   * Extract error message from job execution.
   *
   * Checks for:
   * 1. Exit status description
   * 2. Failed step exception messages
   * 3. Job-level exception messages
   */
  private fun extractErrorMessage(jobExecution: JobExecution): String {
    // First, check exit status description
    jobExecution.exitStatus.exitDescription?.let { description ->
      if (description.isNotBlank()) {
        return description
      }
    }

    // Second, check for failed step exceptions
    jobExecution.stepExecutions
      .filter { it.status == BatchStatus.FAILED }
      .forEach { stepExecution ->
        stepExecution.failureExceptions.firstOrNull()?.let { exception ->
          return "${stepExecution.stepName}: ${exception.message ?: exception.javaClass.simpleName}"
        }
      }

    // Third, check for job-level exceptions
    jobExecution.allFailureExceptions.firstOrNull()?.let { exception ->
      return exception.message ?: exception.javaClass.simpleName
    }

    // Fallback
    return "Job failed without detailed error message"
  }
}
