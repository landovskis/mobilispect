package com.mobilispect.backend.feed.batch

import com.mobilispect.backend.feed.integration.TransitLandFeedSummary
import com.mobilispect.backend.feed.model.FeedEntity
import com.mobilispect.backend.feed.model.FeedSpecType
import org.springframework.batch.core.Job
import org.springframework.batch.core.Step
import org.springframework.batch.core.job.builder.JobBuilder
import org.springframework.batch.core.launch.support.RunIdIncrementer
import org.springframework.batch.core.repository.JobRepository
import org.springframework.batch.core.step.builder.StepBuilder
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.transaction.PlatformTransactionManager

/**
 * Spring Batch configuration for feed discovery jobs.
 *
 * This configuration defines batch jobs for discovering and importing GTFS/GTFS-RT feeds
 * from Transit.land API into the database.
 *
 * Architecture:
 * - ItemReader: Fetches feeds from Transit.land API (paginated)
 * - ItemProcessor: Validates and transforms feed data
 * - ItemWriter: Persists feeds to database in chunks
 *
 * Benefits of Spring Batch:
 * - Chunking: Process feeds in configurable batch sizes
 * - Transaction management: Automatic rollback on failures
 * - Retry logic: Configurable retry for transient failures
 * - Skip logic: Continue processing on individual item failures
 * - Monitoring: Built-in job execution metrics
 * - Restartability: Resume failed jobs from last checkpoint
 */
@Configuration
class FeedDiscoveryBatchConfiguration {

    companion object {
        const val FEED_DISCOVERY_JOB_NAME = "feedDiscoveryJob"
        const val REGIONAL_FEED_DISCOVERY_JOB_NAME = "regionalFeedDiscoveryJob"
        const val CHUNK_SIZE = 10
    }

    /**
     * Global feed discovery job - discovers all feeds from Transit.land.
     */
    @Bean
    fun feedDiscoveryJob(
        jobRepository: JobRepository,
        feedDiscoveryStep: Step
    ): Job {
        return JobBuilder(FEED_DISCOVERY_JOB_NAME, jobRepository)
            .incrementer(RunIdIncrementer())
            .start(feedDiscoveryStep)
            .build()
    }

    /**
     * Regional feed discovery job - discovers feeds for a specific region.
     */
    @Bean
    fun regionalFeedDiscoveryJob(
        jobRepository: JobRepository,
        regionalFeedDiscoveryStep: Step
    ): Job {
        return JobBuilder(REGIONAL_FEED_DISCOVERY_JOB_NAME, jobRepository)
            .incrementer(RunIdIncrementer())
            .start(regionalFeedDiscoveryStep)
            .build()
    }

    /**
     * Step for global feed discovery.
     */
    @Bean
    fun feedDiscoveryStep(
        jobRepository: JobRepository,
        transactionManager: PlatformTransactionManager,
        feedDiscoveryReader: FeedDiscoveryReader,
        feedDiscoveryProcessor: FeedDiscoveryProcessor,
        feedDiscoveryWriter: FeedDiscoveryWriter
    ): Step {
        return StepBuilder("feedDiscoveryStep", jobRepository)
            .chunk<TransitLandFeedSummary, FeedEntity>(CHUNK_SIZE, transactionManager)
            .reader(feedDiscoveryReader)
            .processor(feedDiscoveryProcessor)
            .writer(feedDiscoveryWriter)
            .faultTolerant()
            .skip(Exception::class.java)
            .skipLimit(100) // Allow up to 100 feed failures
            .build()
    }

    /**
     * Step for regional feed discovery.
     */
    @Bean
    fun regionalFeedDiscoveryStep(
        jobRepository: JobRepository,
        transactionManager: PlatformTransactionManager,
        regionalFeedDiscoveryReader: RegionalFeedDiscoveryReader,
        feedDiscoveryProcessor: FeedDiscoveryProcessor,
        feedDiscoveryWriter: FeedDiscoveryWriter
    ): Step {
        return StepBuilder("regionalFeedDiscoveryStep", jobRepository)
            .chunk<TransitLandFeedSummary, FeedEntity>(CHUNK_SIZE, transactionManager)
            .reader(regionalFeedDiscoveryReader)
            .processor(feedDiscoveryProcessor)
            .writer(feedDiscoveryWriter)
            .faultTolerant()
            .skip(Exception::class.java)
            .skipLimit(50) // Allow up to 50 feed failures per region
            .build()
    }
}
