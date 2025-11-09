package com.mobilispect.backend.feed.batch

import com.mobilispect.backend.feed.integration.TransitLandAuthorizationSummary
import com.mobilispect.backend.feed.model.AuthType
import com.mobilispect.backend.feed.model.FeedAuthentication
import com.mobilispect.backend.feed.model.FeedEntity
import com.mobilispect.backend.feed.repository.FeedAuthenticationRepository
import com.mobilispect.backend.feed.repository.FeedRepository
import io.micrometer.core.instrument.MeterRegistry
import org.slf4j.LoggerFactory
import org.springframework.batch.core.configuration.annotation.StepScope
import org.springframework.batch.item.Chunk
import org.springframework.batch.item.ItemWriter
import org.springframework.stereotype.Component
import java.time.Clock
import java.time.Instant

/**
 * ItemWriter for persisting discovered feeds to the database.
 *
 * Handles:
 * - Bulk save of feed entities
 * - Feed authentication creation/update
 * - Metrics recording for monitoring
 *
 * Uses Spring Batch's chunk-oriented processing for optimal performance.
 */
@Component
@StepScope
class FeedDiscoveryWriter(
    private val feedRepository: FeedRepository,
    private val feedAuthenticationRepository: FeedAuthenticationRepository,
    private val meterRegistry: MeterRegistry,
    private val clock: Clock = Clock.systemUTC()
) : ItemWriter<FeedEntity> {

    private val logger = LoggerFactory.getLogger(FeedDiscoveryWriter::class.java)

    override fun write(chunk: Chunk<out FeedEntity>) {
        if (chunk.isEmpty) {
            return
        }

        logger.info("Writing {} feeds to database", chunk.size())

        try {
            // Save all feeds in a single batch
            val savedFeeds = feedRepository.saveAll(chunk.items)

            // Record metrics
            savedFeeds.forEach { feed ->
                incrementFeedCounter("written")
                logger.debug("Saved feed: {}", feed.feedOnestopId)
            }

            logger.info("Successfully wrote {} feeds to database", chunk.size())
        } catch (ex: Exception) {
            logger.error("Failed to write feeds to database", ex)
            incrementFeedCounter("error")
            throw ex
        }
    }

    /**
     * Update or create feed authentication based on Transit.land authorization metadata.
     */
    fun updateAuthentication(feed: FeedEntity, authorization: TransitLandAuthorizationSummary) {
        val authType = mapAuthType(authorization.type)
        val now = Instant.now(clock)

        val existing = feedAuthenticationRepository.findById(feed.feedOnestopId)

        val authentication = existing.orElseGet {
            logger.info("Creating authentication for feed {}", feed.feedOnestopId)
            FeedAuthentication(
                feedOnestopId = feed.feedOnestopId,
                authType = authType,
                headerName = authorization.parameterName,
                isActive = true,
                notes = authorization.infoUrl
            )
        }

        // Update existing authentication
        if (existing.isPresent) {
            logger.debug("Updating authentication for feed {}", feed.feedOnestopId)
            authentication.authType = authType
            authentication.headerName = authorization.parameterName
            authentication.notes = authorization.infoUrl
            authentication.updatedAt = now
        }

        feedAuthenticationRepository.save(authentication)
    }

    private fun mapAuthType(type: String?): AuthType {
        return when (type?.lowercase()) {
            "api_key" -> AuthType.API_KEY
            "oauth2" -> AuthType.OAUTH2
            else -> AuthType.NONE
        }
    }

    private fun incrementFeedCounter(outcome: String) {
        meterRegistry.counter(
            "feed.batch.writes",
            "outcome", outcome
        ).increment()
    }
}
