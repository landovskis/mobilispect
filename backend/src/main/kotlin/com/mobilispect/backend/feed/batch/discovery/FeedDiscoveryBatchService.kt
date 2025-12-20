package com.mobilispect.backend.feed.batch.discovery

import com.mobilispect.backend.feed.model.FeedSpecType
import org.slf4j.LoggerFactory
import org.springframework.batch.core.job.Job
import org.springframework.batch.core.job.parameters.JobParameters
import org.springframework.batch.core.job.parameters.JobParametersBuilder
import org.springframework.batch.core.launch.JobLauncher
import org.springframework.stereotype.Service
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset

/**
 * Service for executing feed discovery batch jobs.
 *
 * This service provides a high-level API for triggering feed discovery
 * from Transit.land, handling job parameter construction and execution.
 */
@Service
class FeedDiscoveryBatchService(
    private val jobLauncher: JobLauncher,
    private val simplifiedFeedDiscoveryJob: Job
) {

    private val logger = LoggerFactory.getLogger(FeedDiscoveryBatchService::class.java)

    /**
     * Discovers all feeds from Transit.land for the specified spec type.
     *
     * @param specType Feed specification type (GTFS or GTFS-RT)
     * @param apiKey Transit.land API key (optional, will use default if not provided)
     * @return Result containing statistics about the discovery operation
     */
    suspend fun discoverAll(
        specType: FeedSpecType,
        apiKey: TransitLandAPIKey? = null
    ): FeedDiscoveryJobResult {
        logger.info("Starting feed discovery for spec type: {}", specType)

        val jobParameters = buildJobParameters(
            specType = specType.dbValue,
            apiKey = apiKey
        )

        return try {
            val jobExecution = jobLauncher.run(simplifiedFeedDiscoveryJob, jobParameters)

            val feedsFound = extractFeedsFound(jobExecution)
            val regionsFound = extractRegionsFound(jobExecution)
            val timeTaken = computeDuration(jobExecution)

            val result = FeedDiscoveryJobResult(
                jobExecutionId = jobExecution.id,
                status = jobExecution.status.name,
                startTime = (jobExecution.startTime as? LocalDateTime)?.toInstant(ZoneOffset.UTC) ?: Instant.now(),
                endTime = (jobExecution.endTime as? LocalDateTime)?.toInstant(ZoneOffset.UTC),
                feedsDiscovered = feedsFound,
                feedsCreated = extractFeedsCreatedCount(jobExecution),
                feedsUpdated = extractFeedsUpdatedCount(jobExecution),
                feedsFound = feedsFound,
                regionsFound = regionsFound,
                timeTakenMillis = timeTaken,
                errors = jobExecution.allFailureExceptions.map { it.message ?: "Unknown error" }
            )

            logger.info(
                "Feed discovery completed: {} feeds discovered, {} created, {} updated",
                result.feedsDiscovered,
                result.feedsCreated,
                result.feedsUpdated
            )

            result
        } catch (e: Exception) {
            logger.error("Feed discovery job failed", e)
            FeedDiscoveryJobResult(
                jobExecutionId = -1,
                status = "FAILED",
                startTime = Instant.now(),
                endTime = Instant.now(),
                feedsDiscovered = 0,
                feedsCreated = 0,
                feedsUpdated = 0,
                feedsFound = 0,
                regionsFound = 0,
                timeTakenMillis = null,
                errors = listOf(e.message ?: "Unknown error")
            )
        }
    }

    /**
     * Discovers feeds for a specific region.
     *
     * @param regionOnestopId Region identifier
     * @param specType Feed specification type
     * @param apiKey Transit.land API key (optional)
     */
    suspend fun discoverForRegion(
        regionOnestopId: String,
        specType: FeedSpecType,
        apiKey: String? = null
    ): FeedDiscoveryJobResult {
        logger.info("Starting feed discovery for region: {}, spec type: {}", regionOnestopId, specType)

        val jobParameters = buildJobParameters(
            specType = specType.dbValue,
            apiKey = TransitLandAPIKey.fromNullable(apiKey),
            regionId = regionOnestopId
        )

        return try {
            val jobExecution = jobLauncher.run(simplifiedFeedDiscoveryJob, jobParameters)

            val feedsFound = extractFeedsFound(jobExecution)
            val regionsFound = extractRegionsFound(jobExecution)
            val timeTaken = computeDuration(jobExecution)

            FeedDiscoveryJobResult(
                jobExecutionId = jobExecution.id,
                status = jobExecution.status.name,
                startTime = (jobExecution.startTime as? LocalDateTime)?.toInstant(ZoneOffset.UTC) ?: Instant.now(),
                endTime = (jobExecution.endTime as? LocalDateTime)?.toInstant(ZoneOffset.UTC),
                feedsDiscovered = feedsFound,
                feedsCreated = extractFeedsCreatedCount(jobExecution),
                feedsUpdated = extractFeedsUpdatedCount(jobExecution),
                feedsFound = feedsFound,
                regionsFound = regionsFound,
                timeTakenMillis = timeTaken,
                errors = jobExecution.allFailureExceptions.map { it.message ?: "Unknown error" }
            )
        } catch (e: Exception) {
            logger.error("Feed discovery job failed for region: {}", regionOnestopId, e)
            FeedDiscoveryJobResult(
                jobExecutionId = -1,
                status = "FAILED",
                startTime = Instant.now(),
                endTime = Instant.now(),
                feedsDiscovered = 0,
                feedsCreated = 0,
                feedsUpdated = 0,
                feedsFound = 0,
                regionsFound = 0,
                timeTakenMillis = null,
                errors = listOf(e.message ?: "Unknown error")
            )
        }
    }

    private fun buildJobParameters(
        specType: String,
        apiKey: TransitLandAPIKey?,
        regionId: String? = null
    ): JobParameters {
        val builder = JobParametersBuilder()
            .addString("specType", specType)
            .addLong("timestamp", System.currentTimeMillis()) // Make each run unique

        apiKey?.let { builder.addString("apiKey", it.value) }
        regionId?.let { builder.addString("regionId", it) }

        return builder.toJobParameters()
    }

    private fun extractFeedsDiscoveredCount(jobExecution: org.springframework.batch.core.job.JobExecution): Int {
        val contextFeeds = jobExecution.executionContext.getInt("feedsFound", -1)
        if (contextFeeds >= 0) {
            return contextFeeds
        }
        return jobExecution.stepExecutions
            .flatMap { it.writeCount.toInt().let { count -> listOf(count) } }
            .sum()
    }

    private fun extractFeedsCreatedCount(jobExecution: org.springframework.batch.core.job.JobExecution): Int {
        // Extract from execution context if stored
        return jobExecution.executionContext.getInt("feedsCreated", 0)
    }

    private fun extractFeedsUpdatedCount(jobExecution: org.springframework.batch.core.job.JobExecution): Int {
        // Extract from execution context if stored
        return jobExecution.executionContext.getInt("feedsUpdated", 0)
    }

    private fun extractFeedsFound(jobExecution: org.springframework.batch.core.job.JobExecution): Int {
        return jobExecution.executionContext.getInt("feedsFound", extractFeedsDiscoveredCount(jobExecution))
    }

    private fun extractRegionsFound(jobExecution: org.springframework.batch.core.job.JobExecution): Int {
        return jobExecution.executionContext.getInt("regionsFound", 0)
    }

    private fun computeDuration(jobExecution: org.springframework.batch.core.job.JobExecution): Long? {
        val startInstant = (jobExecution.startTime as? LocalDateTime)?.toInstant(ZoneOffset.UTC)
        val endInstant = (jobExecution.endTime as? LocalDateTime)?.toInstant(ZoneOffset.UTC)
        return if (startInstant != null && endInstant != null) {
            endInstant.toEpochMilli() - startInstant.toEpochMilli()
        } else {
            null
        }
    }
}

/**
 * Result of a feed discovery job execution.
 *
 * @property jobExecutionId Spring Batch job execution ID
 * @property status Job execution status (COMPLETED, FAILED, etc.)
 * @property startTime Job start timestamp
 * @property endTime Job end timestamp
 * @property feedsDiscovered Total number of feeds discovered
 * @property feedsCreated Number of new feeds created in database
 * @property feedsUpdated Number of existing feeds updated
 * @property errors List of error messages if any occurred
 */
data class FeedDiscoveryJobResult(
    val jobExecutionId: Long,
    val status: String,
    val startTime: Instant,
    val endTime: Instant?,
    val feedsDiscovered: Int,
    val feedsCreated: Int,
    val feedsUpdated: Int,
    val feedsFound: Int,
    val regionsFound: Int,
    val timeTakenMillis: Long?,
    val errors: List<String>
) {
    val duration: Long?
        get() = endTime?.let { it.toEpochMilli() - startTime.toEpochMilli() }

    val isSuccessful: Boolean
        get() = status == "COMPLETED" && errors.isEmpty()
}
