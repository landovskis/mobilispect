package com.mobilispect.backend.feed.service

import com.mobilispect.backend.feed.batch.FeedDiscoveryBatchService
import com.mobilispect.backend.feed.model.FeedSpecType
import kotlinx.coroutines.runBlocking
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service

/**
 * Scheduled job that keeps feed metadata synchronised with Transit.land on a daily cadence.
 *
 * Implements Task T016H: daily feed discovery sync.
 *
 * Uses Spring Batch for efficient, fault-tolerant feed discovery with:
 * - Chunked processing for memory efficiency
 * - Automatic retry and skip logic for transient failures
 * - Job execution history and monitoring
 * - Restartability for failed jobs
 */
@Service
class FeedDiscoveryScheduler(
    private val feedDiscoveryBatchService: FeedDiscoveryBatchService
) {
    private val logger = LoggerFactory.getLogger(FeedDiscoveryScheduler::class.java)

    /**
     * Discover all feeds from Transit.land globally.
     * The batch job automatically assigns feeds to appropriate regions based on operator geography.
     * Default schedule: 01:15 AM server time.
     */
    @Scheduled(cron = "\${feed-management.scheduler.discovery-cron:0 15 1 * * *}")
    fun runDailyDiscovery() = runBlocking {
        logger.info("Starting daily global feed discovery via Spring Batch")

        runCatching {
            feedDiscoveryBatchService.discoverAll(specType = FeedSpecType.GTFS)
        }.onSuccess { result ->
            logger.info(
                "Global feed discovery completed (feeds={}, created={}, updated={}, errors={})",
                result.feedsDiscovered,
                result.feedsCreated,
                result.feedsUpdated,
                result.errors.size
            )
            if (result.errors.isNotEmpty()) {
                logger.warn("Feed discovery errors: {}", result.errors.joinToString("; "))
            }
        }.onFailure { throwable ->
            logger.error("Global feed discovery failed", throwable)
        }
    }
}
