package com.mobilispect.backend.feed.batch

import com.mobilispect.backend.feed.integration.TransitLandApiClient
import com.mobilispect.backend.feed.integration.TransitLandFeedSummary
import com.mobilispect.backend.feed.model.FeedSpecType
import kotlinx.coroutines.runBlocking
import org.slf4j.LoggerFactory
import org.springframework.batch.core.configuration.annotation.StepScope
import org.springframework.batch.item.ItemReader
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import java.util.concurrent.ConcurrentLinkedQueue

/**
 * ItemReader for global feed discovery from Transit.land.
 *
 * Fetches all feeds from Transit.land API and provides them for batch processing.
 * Uses a queue to cache feeds in memory for efficient batch processing.
 *
 * Thread-safe: Uses ConcurrentLinkedQueue for concurrent access.
 */
@Component
@StepScope
class FeedDiscoveryReader(
    private val transitLandApiClient: TransitLandApiClient,
    @Value("#{jobParameters['specType'] ?: 'GTFS'}") private val specTypeParam: String,
    @Value("#{jobParameters['maxFeeds'] ?: 2147483647}") private val maxFeeds: Int
) : ItemReader<TransitLandFeedSummary> {

    private val logger = LoggerFactory.getLogger(FeedDiscoveryReader::class.java)
    private val feedQueue = ConcurrentLinkedQueue<TransitLandFeedSummary>()
    private var initialized = false

    override fun read(): TransitLandFeedSummary? {
        if (!initialized) {
            initialize()
        }
        return feedQueue.poll()
    }

    private fun initialize() {
        logger.info("Initializing global feed discovery reader for spec type: {}", specTypeParam)
        val specType = FeedSpecType.valueOf(specTypeParam)

        runBlocking {
            try {
                val feeds = transitLandApiClient.discoverAllFeeds(specType, maxFeeds)
                logger.info("Discovered {} feeds from Transit.land", feeds.size)
                feedQueue.addAll(feeds)
            } catch (ex: Exception) {
                logger.error("Failed to fetch feeds from Transit.land", ex)
                throw ex
            }
        }

        initialized = true
    }
}

/**
 * ItemReader for regional feed discovery from Transit.land.
 *
 * Fetches feeds for a specific region from Transit.land API.
 */
@Component
@StepScope
class RegionalFeedDiscoveryReader(
    private val transitLandApiClient: TransitLandApiClient,
    @Value("#{jobParameters['regionName']}") private val regionName: String,
    @Value("#{jobParameters['specType'] ?: 'GTFS'}") private val specTypeParam: String
) : ItemReader<TransitLandFeedSummary> {

    private val logger = LoggerFactory.getLogger(RegionalFeedDiscoveryReader::class.java)
    private val feedQueue = ConcurrentLinkedQueue<TransitLandFeedSummary>()
    private var initialized = false

    override fun read(): TransitLandFeedSummary? {
        if (!initialized) {
            initialize()
        }
        return feedQueue.poll()
    }

    private fun initialize() {
        logger.info("Initializing regional feed discovery reader for region: {}, spec type: {}", regionName, specTypeParam)
        val specType = FeedSpecType.valueOf(specTypeParam)

        runBlocking {
            try {
                val feeds = transitLandApiClient.discoverRegionalFeeds(regionName, specType)
                logger.info("Discovered {} feeds for region {}", feeds.size, regionName)
                feedQueue.addAll(feeds)
            } catch (ex: Exception) {
                logger.error("Failed to fetch feeds for region {} from Transit.land", regionName, ex)
                throw ex
            }
        }

        initialized = true
    }
}
