package com.mobilispect.backend.feed.service

import com.mobilispect.backend.feed.repository.MetropolitanRegionRepository
import kotlinx.coroutines.runBlocking
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service

/**
 * Scheduled job that keeps feed metadata synchronised with Transit.land on a daily cadence.
 *
 * Implements Task T016H: daily feed discovery sync.
 */
@Service
class FeedDiscoveryScheduler(
    private val regionRepository: MetropolitanRegionRepository,
    private val feedDiscoveryService: FeedDiscoveryService
) {
    private val logger = LoggerFactory.getLogger(FeedDiscoveryScheduler::class.java)

    /**
     * Discover feeds for all regions with automatic updates enabled.
     * Default schedule: 01:15 AM server time.
     */
    @Scheduled(cron = "\${feed-management.scheduler.discovery-cron:0 15 1 * * *}")
    fun runDailyDiscovery() = runBlocking {
        val regions = regionRepository.findAllByAutoUpdateEnabled(true)
        logger.info("Starting daily feed discovery for {} regions", regions.size)

        regions.forEach { region ->
            runCatching {
                feedDiscoveryService.discover(region.regionOnestopId)
            }.onSuccess {
                logger.info(
                    "Feed discovery completed for region {} (feeds={}, created={}, updated={}, errors={})",
                    region.regionOnestopId,
                    it.feedsDiscovered,
                    it.feedsCreated,
                    it.feedsUpdated,
                    it.errors.size
                )
            }.onFailure { throwable ->
                logger.error("Feed discovery failed for region {}", region.regionOnestopId, throwable)
            }
        }
    }
}
