package com.mobilispect.backend.region.batch

import com.mobilispect.backend.region.data.repository.RegionImportRepository
import com.mobilispect.backend.region.domain.RegionImportId
import com.mobilispect.backend.region.domain.RegionImportStatus
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
 * Finalization tasklet that determines the final status of a region import.
 *
 * This tasklet runs after all feed imports have completed (or failed) and:
 * 1. Evaluates the completed/failed counts from worker steps
 * 2. Sets the appropriate final status:
 *     - COMPLETED: All feeds succeeded
 *     - PARTIAL_SUCCESS: Some succeeded, some failed
 *     - FAILED: All feeds failed
 * 3. Sets the completion timestamp
 *
 * The status is determined from the RegionImport entity which was updated by worker tasklets during
 * parallel processing.
 *
 * Constitutional Requirements:
 * - Continue-on-failure: Partial success is a valid terminal state
 * - Observability: Logs final status and statistics
 */
@Component
@StepScope
class RegionImportFinalizationTasklet(
  private val regionImportRepository: RegionImportRepository,
  @Value("#{jobParameters['regionImportId']}") private val regionImportIdStr: String? = null,
) : Tasklet {

  private val logger = LoggerFactory.getLogger(RegionImportFinalizationTasklet::class.java)

  override fun execute(contribution: StepContribution, chunkContext: ChunkContext): RepeatStatus {
    val regionImportId =
      regionImportIdStr?.let { RegionImportId.fromString(it) }
        ?: throw IllegalStateException("regionImportId job parameter is required")

    logger.info("Finalizing region import {}", regionImportId.value)

    val status = finalizeImport(regionImportId)

    logger.info("Region import {} finalized with status: {}", regionImportId.value, status)

    // Set job exit status based on import status
    contribution.exitStatus =
      when (status) {
        RegionImportStatus.COMPLETED -> ExitStatus.COMPLETED
        RegionImportStatus.PARTIAL_SUCCESS -> ExitStatus("PARTIAL_SUCCESS")
        RegionImportStatus.FAILED -> ExitStatus.FAILED
        else -> ExitStatus.COMPLETED // Fallback for edge cases
      }

    return RepeatStatus.FINISHED
  }

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  fun finalizeImport(regionImportId: RegionImportId): RegionImportStatus {
    val regionImport =
      regionImportRepository.findByImportId(regionImportId).orElseThrow {
        IllegalStateException("Region import not found: $regionImportId")
      }

    // Log current state before finalization
    logger.info(
      "Region import {} statistics: total={}, completed={}, failed={}, skipped={}",
      regionImportId.value,
      regionImport.totalFeeds,
      regionImport.completedCount,
      regionImport.failedCount,
      regionImport.skippedCount,
    )

    // Finalize determines the status based on counts
    regionImport.finalize()
    regionImportRepository.save(regionImport)

    return regionImport.status
  }
}
