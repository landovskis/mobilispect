package com.mobilispect.backend.feed.batch

import com.mobilispect.backend.feed.model.FeedSpecType
import org.slf4j.LoggerFactory
import org.springframework.batch.core.Job
import org.springframework.batch.core.JobParametersBuilder
import org.springframework.batch.core.launch.JobLauncher
import org.springframework.stereotype.Service
import java.time.Instant

/**
 * Service for launching feed discovery batch jobs.
 *
 * Provides a high-level API for executing feed discovery using Spring Batch,
 * abstracting the complexity of job parameter building and job execution.
 *
 * Benefits over manual processing:
 * - Automatic transaction management per chunk
 * - Built-in retry and skip logic for failures
 * - Job execution history and monitoring
 * - Restartability for failed jobs
 * - Metrics and progress tracking
 */
@Service
class FeedDiscoveryBatchService(
    private val jobLauncher: JobLauncher,
    private val feedDiscoveryJob: Job,
    private val regionalFeedDiscoveryJob: Job
) {
    private val logger = LoggerFactory.getLogger(FeedDiscoveryBatchService::class.java)

    /**
     * Discover all feeds from Transit.land using Spring Batch.
     *
     * @param specType Type of feed to discover (GTFS or GTFS-RT)
     * @param maxFeeds Maximum number of feeds to discover
     * @return Summary of discovery results
     */
    suspend fun discoverAll(
        specType: FeedSpecType = FeedSpecType.GTFS,
        maxFeeds: Int = Int.MAX_VALUE
    ): FeedDiscoveryResult {
        logger.info("Starting global feed discovery batch job for spec type: {}", specType)

        val jobParameters = JobParametersBuilder()
            .addString("specType", specType.name)
            .addLong("maxFeeds", maxFeeds.toLong())
            .addLong("timestamp", Instant.now().toEpochMilli()) // Make job unique
            .toJobParameters()

        return try {
            val execution = jobLauncher.run(feedDiscoveryJob, jobParameters)

            logger.info(
                "Feed discovery job completed with status: {}",
                execution.status
            )

            // Extract results from job execution context
            val stepExecution = execution.stepExecutions.firstOrNull()
            val readCount = stepExecution?.readCount ?: 0
            val writeCount = stepExecution?.writeCount ?: 0
            val skipCount = stepExecution?.skipCount ?: 0

            FeedDiscoveryResult(
                regionOnestopId = "global",
                feedsDiscovered = readCount.toInt(),
                feedsCreated = writeCount.toInt(),
                feedsUpdated = 0, // TODO: Track creates vs updates
                errors = if (skipCount > 0) listOf("$skipCount feeds failed processing") else emptyList()
            )
        } catch (ex: Exception) {
            logger.error("Feed discovery batch job failed", ex)
            FeedDiscoveryResult(
                regionOnestopId = "global",
                feedsDiscovered = 0,
                feedsCreated = 0,
                feedsUpdated = 0,
                errors = listOf(ex.message ?: "Batch job execution failed")
            )
        }
    }

    /**
     * Discover feeds for a specific region using Spring Batch.
     *
     * @param regionOnestopId Region onestop ID
     * @param regionName Region name for Transit.land query
     * @param specType Type of feed to discover (GTFS or GTFS-RT)
     * @return Summary of discovery results
     */
    suspend fun discover(
        regionOnestopId: String,
        regionName: String,
        specType: FeedSpecType = FeedSpecType.GTFS
    ): FeedDiscoveryResult {
        logger.info(
            "Starting regional feed discovery batch job for region: {}, spec type: {}",
            regionOnestopId,
            specType
        )

        val jobParameters = JobParametersBuilder()
            .addString("regionOnestopId", regionOnestopId)
            .addString("regionName", regionName)
            .addString("specType", specType.name)
            .addLong("timestamp", Instant.now().toEpochMilli()) // Make job unique
            .toJobParameters()

        return try {
            val execution = jobLauncher.run(regionalFeedDiscoveryJob, jobParameters)

            logger.info(
                "Regional feed discovery job completed with status: {}",
                execution.status
            )

            // Extract results from job execution context
            val stepExecution = execution.stepExecutions.firstOrNull()
            val readCount = stepExecution?.readCount ?: 0
            val writeCount = stepExecution?.writeCount ?: 0
            val skipCount = stepExecution?.skipCount ?: 0

            FeedDiscoveryResult(
                regionOnestopId = regionOnestopId,
                feedsDiscovered = readCount.toInt(),
                feedsCreated = writeCount.toInt(),
                feedsUpdated = 0, // TODO: Track creates vs updates
                errors = if (skipCount > 0) listOf("$skipCount feeds failed processing") else emptyList()
            )
        } catch (ex: Exception) {
            logger.error("Regional feed discovery batch job failed for region {}", regionOnestopId, ex)
            FeedDiscoveryResult(
                regionOnestopId = regionOnestopId,
                feedsDiscovered = 0,
                feedsCreated = 0,
                feedsUpdated = 0,
                errors = listOf(ex.message ?: "Batch job execution failed")
            )
        }
    }
}

/**
 * Result of a feed discovery operation.
 */
data class FeedDiscoveryResult(
    val regionOnestopId: String,
    val feedsDiscovered: Int,
    val feedsCreated: Int,
    val feedsUpdated: Int,
    val errors: List<String>
)
