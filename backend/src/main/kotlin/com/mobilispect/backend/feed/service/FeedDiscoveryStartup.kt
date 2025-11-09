package com.mobilispect.backend.feed.service

import com.mobilispect.backend.feed.model.FeedSpecType
import com.mobilispect.backend.feed.repository.FeedRepository
import com.mobilispect.backend.feed.repository.MetropolitanRegionRepository
import kotlinx.coroutines.runBlocking
import org.slf4j.LoggerFactory
import org.springframework.boot.context.event.ApplicationReadyEvent
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Component

/**
 * Runs initial feed discovery for regions that have never been discovered.
 *
 * This component listens for application startup and automatically discovers feeds
 * for any regions that have auto-update enabled but have zero feeds. This ensures
 * that newly added regions or fresh installations get populated with feed data.
 */
@Component
class FeedDiscoveryStartup(
    private val regionRepository: MetropolitanRegionRepository,
    private val feedRepository: FeedRepository,
    private val feedDiscoveryService: FeedDiscoveryService
) {
    private val logger = LoggerFactory.getLogger(FeedDiscoveryStartup::class.java)

    @EventListener(ApplicationReadyEvent::class)
    fun runInitialDiscovery() {
        logger.info("Checking if initial global feed discovery is needed...")

        runBlocking {
            val totalFeeds = feedRepository.count()

            if (totalFeeds > 0) {
                logger.info("Feed database already populated with {} feeds. Skipping initial discovery.", totalFeeds)
                return@runBlocking
            }

            logger.info("Feed database is empty. Starting global feed discovery from Transit.land...")

            // Discover both GTFS and GTFS-RT feeds globally
            listOf(FeedSpecType.GTFS, FeedSpecType.GTFS_RT).forEach { specType ->
                runCatching {
                    feedDiscoveryService.discoverAll(specType)
                }.onSuccess { result ->
                    logger.info(
                        "Global {} discovery completed: discovered={}, created={}, updated={}, errors={}",
                        specType,
                        result.feedsDiscovered,
                        result.feedsCreated,
                        result.feedsUpdated,
                        result.errors.size
                    )

                    if (result.errors.isNotEmpty()) {
                        logger.warn("Discovery encountered {} errors:", result.errors.size)
                        result.errors.take(10).forEach { error ->
                            logger.warn("  - {}", error)
                        }
                        if (result.errors.size > 10) {
                            logger.warn("  ... and {} more errors", result.errors.size - 10)
                        }
                    }
                }.onFailure { throwable ->
                    logger.error(
                        "Global {} discovery failed",
                        specType,
                        throwable
                    )
                }
            }

            val finalCount = feedRepository.count()
            logger.info("Initial global feed discovery completed. Total feeds in database: {}", finalCount)
        }
    }
}
