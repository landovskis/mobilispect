package com.mobilispect.backend.region.batch

import org.springframework.batch.core.job.Job
import org.springframework.batch.core.job.builder.FlowBuilder
import org.springframework.batch.core.job.builder.JobBuilder
import org.springframework.batch.core.job.flow.support.SimpleFlow
import org.springframework.batch.core.partition.support.TaskExecutorPartitionHandler
import org.springframework.batch.core.repository.JobRepository
import org.springframework.batch.core.step.Step
import org.springframework.batch.core.step.builder.StepBuilder
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.task.TaskExecutor
import org.springframework.transaction.PlatformTransactionManager

/**
 * Spring Batch job configuration for region-level bulk imports.
 *
 * This job uses a partitioned step architecture for parallel feed processing:
 * 1. **Initialization Step**: Marks region import as RUNNING, counts feeds
 * 2. **Partitioned Feed Import Step**: Processes feeds in parallel using work-stealing
 * 3. **Finalization Step**: Determines final status (COMPLETED/PARTIAL_SUCCESS/FAILED)
 *
 * The partitioned step uses a ForkJoinPool-based TaskExecutor that implements work-stealing
 * scheduling. This ensures efficient utilization when:
 * - Some feeds take longer to download/parse than others
 * - The number of feeds exceeds the parallelism limit
 *
 * Constitutional Requirements:
 * - Spring Batch integration for observability and restartability
 * - Database persistence for metadata tracking
 * - Continue-on-failure semantics for partial success handling
 * - Performance: Work-stealing maximizes throughput
 */
@Configuration
class RegionImportJobConfig(
  private val jobRepository: JobRepository,
  private val transactionManager: PlatformTransactionManager,
  private val feedPartitioner: FeedPartitioner,
  private val workerTasklet: FeedImportWorkerTasklet,
  private val initializationTasklet: RegionImportInitializationTasklet,
  private val finalizationTasklet: RegionImportFinalizationTasklet,
  private val jobExecutionListener: RegionImportJobExecutionListener,
  @Qualifier("regionImportTaskExecutor") private val taskExecutor: TaskExecutor,
  @Qualifier("regionImportGridSize") private val gridSize: Int,
) {

  /**
   * Defines the region import job.
   *
   * Job Parameters:
   * - regionImportId: UUID of the RegionImport entity
   * - regionOnestopId: Transit.land region identifier
   * - triggerType: MANUAL or AUTOMATIC
   * - timestamp: Unique timestamp to allow multiple runs
   *
   * Flow Transitions:
   * - Uses Flow-based configuration to ensure finalization runs regardless of partition failures
   * - If any partition fails, the partitioned step exits with FAILED status
   * - The finalization step runs anyway (via "*" transition) to determine actual outcome
   * - Finalization reads database state to set COMPLETED, PARTIAL_SUCCESS, or FAILED
   */
  @Bean
  fun regionImportJob(): Job {
    val flow =
      FlowBuilder<SimpleFlow>("regionImportFlow")
        .start(regionImportInitializationStep())
        .on("*")
        .to(feedImportPartitionedStep())
        .from(feedImportPartitionedStep())
        .on("*")
        .to(regionImportFinalizationStep())
        .end()

    return JobBuilder("regionImportJob", jobRepository)
      .preventRestart()
      .start(flow)
      .end()
      .listener(jobExecutionListener)
      .build()
  }

  /**
   * Initialization step - prepares the region import.
   * - Marks region import as RUNNING
   * - Counts active feeds for the region
   * - Stores metadata in job execution context
   */
  @Bean
  fun regionImportInitializationStep(): Step =
    StepBuilder("regionImportInitializationStep", jobRepository)
      .tasklet(initializationTasklet, transactionManager)
      .build()

  /**
   * Partitioned step for parallel feed import processing.
   *
   * The partitioner creates one partition per active feed. The partition handler uses a
   * work-stealing executor to process partitions in parallel:
   * - If feeds <= parallelism limit: All feeds process simultaneously
   * - If feeds > parallelism limit: Excess feeds queue and process as workers free up
   * - Work-stealing: Idle workers take tasks from busy workers' queues
   */
  @Bean
  fun feedImportPartitionedStep(): Step =
    StepBuilder("feedImportPartitionedStep", jobRepository)
      .partitioner("feedImportWorkerStep", feedPartitioner)
      .partitionHandler(feedImportPartitionHandler())
      .build()

  /**
   * Partition handler with work-stealing task executor.
   *
   * Uses ForkJoinPool to implement work-stealing:
   * - Tasks are distributed to worker threads
   * - When a thread finishes, it steals work from other threads' queues
   * - Provides efficient load balancing for feeds with varying processing times
   */
  @Bean
  fun feedImportPartitionHandler(): TaskExecutorPartitionHandler {
    val handler = TaskExecutorPartitionHandler()
    handler.setStep(feedImportWorkerStep())
    handler.setTaskExecutor(taskExecutor)
    handler.setGridSize(gridSize)
    return handler
  }

  /**
   * Worker step that processes a single feed.
   *
   * Each partition (feed) is processed by this step. The step:
   * - Retrieves feed info from partition's ExecutionContext
   * - Downloads and parses the GTFS feed
   * - Persists agencies, routes, and other entities
   * - Updates region import tracking
   */
  @Bean
  fun feedImportWorkerStep(): Step =
    StepBuilder("feedImportWorkerStep", jobRepository)
      .tasklet(workerTasklet, transactionManager)
      .build()

  /**
   * Finalization step - determines final import status.
   *
   * Evaluates completed/failed counts and sets appropriate status:
   * - COMPLETED: All feeds succeeded
   * - PARTIAL_SUCCESS: Some succeeded, some failed
   * - FAILED: All feeds failed
   */
  @Bean
  fun regionImportFinalizationStep(): Step =
    StepBuilder("regionImportFinalizationStep", jobRepository)
      .tasklet(finalizationTasklet, transactionManager)
      .build()
}
