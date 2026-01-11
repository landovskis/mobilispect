package com.mobilispect.backend.feed.service

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

  // Allow only 3 concurrent job launches at a time
  private val concurrencyLimit = Semaphore(3)

  // Minimum delay between job launches in milliseconds
  private val minDelayBetweenLaunchesMs = 100L

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
          logger.debug("Throttling job launch, sleeping for {}ms to maintain minimum delay", delay)
          Thread.sleep(delay)
        }

        lastLaunchTime = System.currentTimeMillis()

        // Launch the job
        delegate.run(job, parameters)
      } finally {
        // Release permit after a short delay to ensure job is fully registered
        Thread.sleep(50)
        concurrencyLimit.release()
      }
    } catch (e: InterruptedException) {
      Thread.currentThread().interrupt()
      throw RuntimeException("Job launch interrupted", e)
    }
  }
}
