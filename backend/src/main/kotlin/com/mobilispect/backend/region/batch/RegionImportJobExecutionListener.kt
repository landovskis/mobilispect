package com.mobilispect.backend.region.batch

import com.mobilispect.backend.region.RegionId
import com.mobilispect.backend.region.domain.RegionImportId
import com.mobilispect.backend.region.service.RegionFeedsImportCompletedEvent
import com.mobilispect.backend.region.service.RegionFeedsImportFailedEvent
import com.mobilispect.backend.region.service.RegionFeedsImportStartedEvent
import org.slf4j.LoggerFactory
import org.springframework.batch.core.BatchStatus
import org.springframework.batch.core.job.JobExecution
import org.springframework.batch.core.listener.JobExecutionListener
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Component

/**
 * Job execution listener for the region import batch job.
 *
 * Provides observability and monitoring for the region import job lifecycle by publishing events,
 * logging progress, and tracking metrics.
 *
 * Constitutional Requirement: Observability & Operational Insight
 * - Traces job execution lifecycle for operational monitoring
 * - Publishes events for downstream observability systems
 * - Logs structured information for debugging and audit trails
 */
@Component
class RegionImportJobExecutionListener(private val eventPublisher: ApplicationEventPublisher) :
  JobExecutionListener {

  private val logger = LoggerFactory.getLogger(RegionImportJobExecutionListener::class.java)

  override fun beforeJob(jobExecution: JobExecution) {
    val regionId = extractRegionId(jobExecution)
    val regionImportId = extractRegionImportId(jobExecution)

    logger.info(
      "Starting region import job for region: {}, import: {}",
      regionId?.value ?: "unknown",
      regionImportId?.value ?: "unknown",
    )

    regionId?.let { id ->
      eventPublisher.publishEvent(RegionFeedsImportStartedEvent(id))
      logger.debug("Published RegionFeedsImportStarted event for region: {}", id.value)
    }
  }

  override fun afterJob(jobExecution: JobExecution) {
    val regionId = extractRegionId(jobExecution)
    val regionImportId = extractRegionImportId(jobExecution)
    val status = jobExecution.status
    val exitStatus = jobExecution.exitStatus

    logger.info(
      "Completed region import job for region: {}, import: {} with status: {} (exit code: {})",
      regionId?.value ?: "unknown",
      regionImportId?.value ?: "unknown",
      status,
      exitStatus.exitCode,
    )

    regionId?.let { id ->
      when (status) {
        BatchStatus.COMPLETED -> {
          // Check exit code for partial success
          val isPartialSuccess = exitStatus.exitCode == "PARTIAL_SUCCESS"
          if (isPartialSuccess) {
            eventPublisher.publishEvent(RegionFeedsImportFailedEvent(id))
            logger.info(
              "Published RegionFeedsImportFailed event (partial success) for region: {}",
              id.value,
            )
          } else {
            eventPublisher.publishEvent(RegionFeedsImportCompletedEvent(id))
            logger.info("Published RegionFeedsImportCompleted event for region: {}", id.value)
          }
        }
        BatchStatus.FAILED -> {
          eventPublisher.publishEvent(RegionFeedsImportFailedEvent(id))
          logger.error("Published RegionFeedsImportFailed event for region: {}", id.value)
        }
        else -> {
          logger.warn(
            "Region import job completed with unexpected status: {} for region: {}",
            status,
            id.value,
          )
        }
      }
    }
  }

  private fun extractRegionId(jobExecution: JobExecution): RegionId? {
    val regionOnestopId = jobExecution.jobParameters.getString("regionOnestopId")

    return when {
      regionOnestopId != null -> RegionId(regionOnestopId)
      else -> {
        logger.warn("Could not extract regionId from job execution")
        null
      }
    }
  }

  private fun extractRegionImportId(jobExecution: JobExecution): RegionImportId? {
    val regionImportIdStr = jobExecution.jobParameters.getString("regionImportId")

    return when {
      regionImportIdStr != null -> RegionImportId.fromString(regionImportIdStr)
      else -> {
        logger.warn("Could not extract regionImportId from job execution")
        null
      }
    }
  }
}
