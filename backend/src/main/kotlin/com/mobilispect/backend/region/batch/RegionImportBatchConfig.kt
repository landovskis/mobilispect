package com.mobilispect.backend.region.batch

import java.util.concurrent.ForkJoinPool
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.task.TaskExecutor
import org.springframework.scheduling.concurrent.ConcurrentTaskExecutor

/**
 * Configuration for region import batch processing.
 *
 * Provides a work-stealing TaskExecutor based on ForkJoinPool for parallel feed import processing.
 * Work stealing ensures efficient utilization of threads when some feeds take longer to process
 * than others.
 *
 * Configuration properties:
 * - region.import.parallelism: Number of parallel worker threads (default: available processors)
 * - region.import.timeout-minutes: Maximum time for a single feed import (default: 30)
 *
 * Constitutional Requirements:
 * - Performance & Reliability: Work-stealing maximizes throughput
 * - Observability: Logging of executor configuration
 */
@Configuration
class RegionImportBatchConfig {

  private val logger = LoggerFactory.getLogger(RegionImportBatchConfig::class.java)

  /**
   * Work-stealing TaskExecutor for parallel feed import processing.
   *
   * Uses ForkJoinPool which implements work-stealing scheduling:
   * - Idle threads "steal" work from busy threads' queues
   * - Efficient for tasks with varying execution times
   * - Bounded parallelism prevents resource exhaustion
   *
   * When there are more feeds than the parallelism limit, the ForkJoinPool automatically queues
   * additional tasks and processes them as workers become available.
   *
   * @param parallelism Maximum number of concurrent feed imports
   */
  @Bean(name = ["regionImportTaskExecutor"])
  fun regionImportTaskExecutor(
    @Value(
      "\${region.import.parallelism:#{T(java.lang.Runtime).getRuntime().availableProcessors()}}"
    )
    parallelism: Int
  ): TaskExecutor {
    logger.info(
      "Creating work-stealing TaskExecutor for region imports with parallelism: {}",
      parallelism,
    )

    // Create a ForkJoinPool with the specified parallelism
    // Using FIFO async mode ensures fair scheduling
    val forkJoinPool =
      ForkJoinPool(
        parallelism,
        ForkJoinPool.defaultForkJoinWorkerThreadFactory,
        { thread, throwable ->
          logger.error(
            "Uncaught exception in region import worker thread: {}",
            thread.name,
            throwable,
          )
        },
        true, // asyncMode = true for FIFO task handling
      )

    logger.info(
      "ForkJoinPool created: parallelism={}, commonPoolParallelism={}",
      forkJoinPool.parallelism,
      ForkJoinPool.getCommonPoolParallelism(),
    )

    return ConcurrentTaskExecutor(forkJoinPool)
  }

  /**
   * Grid size for partitioning.
   *
   * This hints at the expected number of partitions but the actual number is determined by the
   * Partitioner based on active feeds.
   */
  @Bean(name = ["regionImportGridSize"])
  fun regionImportGridSize(
    @Value(
      "\${region.import.parallelism:#{T(java.lang.Runtime).getRuntime().availableProcessors()}}"
    )
    parallelism: Int
  ): Int {
    // Use parallelism as grid size hint, but partitioner determines actual count
    return parallelism
  }
}
