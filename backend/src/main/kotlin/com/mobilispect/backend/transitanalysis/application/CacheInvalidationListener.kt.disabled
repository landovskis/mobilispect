package com.mobilispect.backend.transitanalysis.application

import com.mobilispect.backend.config.RedisConfiguration
import com.mobilispect.backend.feed.events.FeedImportCompleted
import org.slf4j.LoggerFactory
import org.springframework.cache.CacheManager
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Component

/**
 * Event listener that invalidates Redis caches when feed imports complete (T124).
 *
 * When a feed import completes, both agency and frequency caches are invalidated
 * to ensure subsequent queries reflect the newly imported data.
 *
 * Cache invalidation strategy:
 * - Evicts entire cache regions rather than individual keys for simplicity
 * - Frequency cache (1-hour TTL) contains route, variant, and frequency data
 * - Agency cache (24-hour TTL) contains agency listings and summaries
 */
@Component
class CacheInvalidationListener(
    private val cacheManager: CacheManager
) {
    private val logger = LoggerFactory.getLogger(CacheInvalidationListener::class.java)

    /**
     * Invalidates all transit analysis caches when feed import completes.
     *
     * This ensures that:
     * - New routes and variants are immediately available in queries
     * - Updated frequency calculations are reflected
     * - Agency statistics (route counts) are refreshed
     */
    @EventListener
    fun onFeedImportCompleted(event: FeedImportCompleted) {
        logger.info(
            "Feed import completed for feedId={}, invalidating caches (routes={}, variants={})",
            event.feedId.value,
            event.routesProcessed,
            event.variantsIdentified
        )

        try {
            // Invalidate frequency cache (routes, variants, frequencies)
            cacheManager.getCache(RedisConfiguration.FREQUENCY_CACHE)?.clear()
            logger.debug("Cleared {} cache", RedisConfiguration.FREQUENCY_CACHE)

            // Invalidate agency cache (agency listings and summaries)
            cacheManager.getCache(RedisConfiguration.AGENCY_CACHE)?.clear()
            logger.debug("Cleared {} cache", RedisConfiguration.AGENCY_CACHE)

            logger.info("Successfully invalidated all transit analysis caches for feedId={}", event.feedId.value)
        } catch (e: Exception) {
            logger.error(
                "Failed to invalidate caches for feedId={}: {}",
                event.feedId.value,
                e.message,
                e
            )
        }
    }
}
