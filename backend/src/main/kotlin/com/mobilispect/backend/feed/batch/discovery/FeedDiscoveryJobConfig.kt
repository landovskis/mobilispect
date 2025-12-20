package com.mobilispect.backend.feed.batch.discovery

import com.mobilispect.backend.infastructure.transit_land.TransitLandAPI
import org.slf4j.LoggerFactory
import org.springframework.batch.core.job.Job
import org.springframework.batch.core.step.Step
import org.springframework.batch.core.configuration.annotation.StepScope
import org.springframework.batch.core.job.builder.JobBuilder
import org.springframework.batch.core.repository.JobRepository
import org.springframework.batch.core.step.builder.StepBuilder
import org.springframework.batch.infrastructure.item.ItemReader
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.stereotype.Component
import org.springframework.transaction.PlatformTransactionManager

/**
 * Feed discovery job configuration for single-step execution.
 *
 * This configuration combines all feed discovery operations into a single step:
 * 1. Reads operators from Transit.land and maps feeds to regions
 * 2. Fetches detailed feed metadata from Transit.land
 * 3. Processes and combines region and metadata information
 * 4. Writes complete feed data to the database
 *
 * The job is designed to be run periodically (e.g., daily) to keep
 * the feed database synchronized with Transit.land.
 */
@Configuration
class SimplifiedFeedDiscoveryJobConfig(
    private val jobRepository: JobRepository,
    private val transactionManager: PlatformTransactionManager,
    private val feedDiscoveryProcessor: FeedDiscoveryProcessor,
    private val feedDiscoveryWriter: FeedDiscoveryWriter
) {

    /**
     * Simplified single-step feed discovery job.
     *
     * This job processes operators in batches, fetching metadata and
     * writing to the database all in one step for simplicity.
     */
    @Bean
    fun simplifiedFeedDiscoveryJob(
        @Qualifier("simplifiedDiscoveryStep")
        simplifiedDiscoveryStep: Step
    ): Job = JobBuilder("simplifiedFeedDiscoveryJob", jobRepository)
        .start(simplifiedDiscoveryStep)
        .build()

    /**
     * Single step that handles the complete discovery process.
     *
     * This step uses a custom reader that:
     * 1. Reads operator data and creates feed-region map
     * 2. Fetches metadata for discovered feeds
     * 3. Combines both into FeedDiscoveryInput
     *
     * Then processes and writes in a single transaction.
     */
    @Bean
    fun simplifiedDiscoveryStep(
        @Qualifier("combinedFeedDiscoveryReader")
        reader: ItemReader<FeedDiscoveryInput>
    ): Step = StepBuilder("simplifiedDiscoveryStep", jobRepository)
        .chunk<FeedDiscoveryInput, FeedDiscoveryBatch>(10, transactionManager)
        .reader(reader)
        .processor(feedDiscoveryProcessor)
        .writer(feedDiscoveryWriter)
        .build()

    /**
     * Combined reader that integrates operator reading and metadata fetching.
     *
     * This reader internally orchestrates:
     * 1. Reading batches of operators from Transit.land
     * 2. Extracting feed IDs from the operator data
     * 3. Fetching metadata for those feeds via transit land service
     * 4. Combining both into FeedDiscoveryInput for processing
     *
     * Rate limiting and concurrency control are centralized in TransitLandClient.
     */
    @Bean
    @StepScope
    fun combinedFeedDiscoveryReader(
        transitLandClient: TransitLandAPI,
        @Value("#{jobParameters['apiKey']}") apiKeyString: String?,
        @Value("#{jobParameters['specType'] ?: 'gtfs'}") specType: String,
        @Value("#{jobParameters['batchSize'] ?: 100}") batchSize: Int,
        @Value("\${app.transit-land.api-key:}") defaultApiKeyString: String?
    ): ItemReader<FeedDiscoveryInput> {
        val apiKey = TransitLandAPIKey.fromNullable(apiKeyString)
        val defaultApiKey = TransitLandAPIKey.fromNullable(defaultApiKeyString)
        return FeedDiscoveryReader(
            transitLandClient = transitLandClient,
            apiKey = apiKey,
            defaultApiKey = defaultApiKey,
            specType = specType,
            operatorBatchSize = batchSize
        )
    }
}

/**
 * Execution listener for feed discovery jobs.
 *
 * Provides logging and monitoring for job execution lifecycle.
 */
@Component
class FeedDiscoveryJobListener : org.springframework.batch.core.listener.JobExecutionListener {

    private val logger = LoggerFactory.getLogger(FeedDiscoveryJobListener::class.java)

    override fun beforeJob(jobExecution: org.springframework.batch.core.job.JobExecution) {
        logger.info(
            "Starting feed discovery job: {} with parameters: {}",
            jobExecution.jobInstance.jobName,
            jobExecution.jobParameters
        )
    }

    override fun afterJob(jobExecution: org.springframework.batch.core.job.JobExecution) {
        val duration = if (jobExecution.endTime != null && jobExecution.startTime != null) {
            java.time.Duration.between(
                jobExecution.startTime,
                jobExecution.endTime
            ).toMillis()
        } else {
            0L
        }

        logger.info(
            "Feed discovery job completed: {} - Status: {} - Duration: {}ms",
            jobExecution.jobInstance.jobName,
            jobExecution.status,
            duration
        )

        if (jobExecution.status == org.springframework.batch.core.BatchStatus.FAILED) {
            logger.error(
                "Feed discovery job failed with {} errors",
                jobExecution.allFailureExceptions.size
            )
            jobExecution.allFailureExceptions.take(5).forEach { exception ->
                logger.error("Job failure: ", exception)
            }
        }
    }
}
