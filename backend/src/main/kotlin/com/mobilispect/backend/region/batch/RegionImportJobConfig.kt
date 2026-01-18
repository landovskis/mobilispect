package com.mobilispect.backend.region.batch

import org.springframework.batch.core.job.Job
import org.springframework.batch.core.job.builder.JobBuilder
import org.springframework.batch.core.repository.JobRepository
import org.springframework.batch.core.step.Step
import org.springframework.batch.core.step.builder.StepBuilder
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.transaction.PlatformTransactionManager

/**
 * Spring Batch job configuration for region-level bulk imports.
 *
 * This job acts as a parent orchestrator that launches child feed import jobs for all active feeds
 * in a region. The job uses a single TaskletStep that handles:
 * 1. Launching child feed import jobs
 * 2. Tracking progress via database updates
 * 3. Polling for child completion
 * 4. Determining final status (COMPLETED/PARTIAL_SUCCESS/FAILED)
 *
 * Constitutional Requirements:
 * - Spring Batch integration for observability and restartability
 * - Database persistence for metadata tracking
 * - Continue-on-failure semantics for partial success handling
 */
@Configuration
class RegionImportJobConfig(
  private val jobRepository: JobRepository,
  private val transactionManager: PlatformTransactionManager,
  private val orchestrationTasklet: RegionImportOrchestrationTasklet,
  private val jobExecutionListener: RegionImportJobExecutionListener,
) {

  /**
   * Defines the region import job.
   *
   * Job Parameters:
   * - regionImportId: UUID of the RegionImport entity
   * - regionOnestopId: Transit.land region identifier
   * - triggerType: MANUAL or AUTOMATIC
   * - timestamp: Unique timestamp to allow multiple runs
   */
  @Bean
  fun regionImportJob(): Job =
    JobBuilder("regionImportJob", jobRepository)
      .preventRestart()
      .start(regionImportOrchestrationStep())
      .listener(jobExecutionListener)
      .build()

  @Bean
  fun regionImportOrchestrationStep(): Step =
    StepBuilder("regionImportOrchestrationStep", jobRepository)
      .tasklet(orchestrationTasklet, transactionManager)
      .build()
}
