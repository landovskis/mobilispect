package com.mobilispect.backend.feed.service

import jakarta.annotation.PostConstruct
import java.util.concurrent.Semaphore
import org.slf4j.LoggerFactory
import org.springframework.batch.core.job.Job
import org.springframework.batch.core.job.parameters.JobParameters
import org.springframework.batch.core.launch.JobLauncher
import org.springframework.stereotype.Component

/**
 * Rate-limited wrapper around Spring Batch JobLauncher to prevent overwhelming the job execution
 * system with concurrent launches. Implements throttling with configurable delays and concurrency
 * limits.
 *
 * This solves the issue where launching many jobs simultaneously causes Spring Batch's in-memory
 * tracking to incorrectly report JobExecutionAlreadyRunningException.
 */
@Component
class RateLimitedJobLauncher(private val delegate: JobLauncher) {
  private val logger = LoggerFactory.getLogger(RateLimitedJobLauncher::class.java)

  @PostConstruct
  fun init() {
    logger.info("RateLimitedJobLauncher initialized with delegate: ${delegate.javaClass.name}")
  }

  // Allow only 1 concurrent job launch at a time (Spring Batch 6.0 requires sequential launches)
  private val concurrencyLimit = Semaphore(1)

  // Minimum delay between job launches in milliseconds
  private val minDelayBetweenLaunchesMs = 200L

  @Volatile private var lastLaunchTime = 0L

  /**
   * Launch a job with rate limiting. Enforces:
   * 1. Maximum concurrent launches (semaphore)
   * 2. Minimum delay between consecutive launches
   */
  fun run(job: Job, parameters: JobParameters) {
    try {
      // Acquire permit (blocks if 3 jobs already launching)
      concurrencyLimit.acquire()

      try {
        // Enforce minimum delay between launches
        val now = System.currentTimeMillis()
        val timeSinceLastLaunch = now - lastLaunchTime

        if (timeSinceLastLaunch < minDelayBetweenLaunchesMs) {
          val delay = minDelayBetweenLaunchesMs - timeSinceLastLaunch
          logger.info("Throttling job launch, sleeping for {}ms to maintain minimum delay", delay)
          Thread.sleep(delay)
        }

        lastLaunchTime = System.currentTimeMillis()

        // Launch the job
        logger.info("Launching job via delegate JobLauncher")
        delegate.run(job, parameters)
        logger.info("Job launched successfully, waiting for registration")
      } finally {
        // Release permit after delay to ensure job is fully registered in Spring Batch
        Thread.sleep(300)
        concurrencyLimit.release()
      }
    } catch (e: InterruptedException) {
      Thread.currentThread().interrupt()
      throw RuntimeException("Job launch interrupted", e)
    }
  }
}
