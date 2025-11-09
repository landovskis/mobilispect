package com.mobilispect.backend.feed.batch

import org.springframework.batch.core.configuration.annotation.EnableBatchProcessing
import org.springframework.context.annotation.Configuration

/**
 * Enable Spring Batch processing for feed discovery jobs.
 *
 * This configuration enables Spring Batch auto-configuration which provides:
 * - JobRepository for job metadata persistence
 * - JobLauncher for running batch jobs
 * - Transaction management for chunk-oriented processing
 * - Job execution listeners and monitoring
 */
@Configuration
@EnableBatchProcessing
class BatchConfig
