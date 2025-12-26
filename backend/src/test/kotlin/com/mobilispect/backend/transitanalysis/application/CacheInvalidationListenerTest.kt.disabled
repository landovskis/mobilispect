package com.mobilispect.backend.transitanalysis.application

import com.mobilispect.backend.config.RedisConfiguration
import com.mobilispect.backend.feed.model.ids.FeedId
import com.mobilispect.backend.transitanalysis.events.FeedImportCompleted
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.mockito.junit.jupiter.MockitoExtension
import org.springframework.cache.Cache
import org.springframework.cache.CacheManager

/**
 * Unit tests for cache invalidation listener (T124).
 *
 * Verifies that caches are properly cleared when feed imports complete.
 */
@ExtendWith(MockitoExtension::class)
class CacheInvalidationListenerTest {

    private val cacheManager: CacheManager = mock(CacheManager::class.java)
    private val listener = CacheInvalidationListener(cacheManager)

    @Test
    fun `onFeedImportCompleted clears both frequency and agency caches`() {
        // Given
        val frequencyCache: Cache = mock(Cache::class.java)
        val agencyCache: Cache = mock(Cache::class.java)

        `when`(cacheManager.getCache(RedisConfiguration.FREQUENCY_CACHE)).thenReturn(frequencyCache)
        `when`(cacheManager.getCache(RedisConfiguration.AGENCY_CACHE)).thenReturn(agencyCache)

        val event = FeedImportCompleted(
            feedId = FeedId("f-test-feed"),
            routesProcessed = 50,
            variantsIdentified = 120,
            durationMillis = 5000
        )

        // When
        listener.onFeedImportCompleted(event)

        // Then
        verify(frequencyCache).clear()
        verify(agencyCache).clear()
    }

    @Test
    fun `onFeedImportCompleted handles null caches gracefully`() {
        // Given
        `when`(cacheManager.getCache(RedisConfiguration.FREQUENCY_CACHE)).thenReturn(null)
        `when`(cacheManager.getCache(RedisConfiguration.AGENCY_CACHE)).thenReturn(null)

        val event = FeedImportCompleted(
            feedId = FeedId("f-test-feed"),
            routesProcessed = 50,
            variantsIdentified = 120,
            durationMillis = 5000
        )

        // When - should not throw exception
        listener.onFeedImportCompleted(event)

        // Then - completes without error
    }

    @Test
    fun `onFeedImportCompleted continues after cache clear error`() {
        // Given
        val frequencyCache: Cache = mock(Cache::class.java)
        val agencyCache: Cache = mock(Cache::class.java)

        `when`(cacheManager.getCache(RedisConfiguration.FREQUENCY_CACHE)).thenReturn(frequencyCache)
        `when`(cacheManager.getCache(RedisConfiguration.AGENCY_CACHE)).thenReturn(agencyCache)
        `when`(frequencyCache.clear()).thenThrow(RuntimeException("Redis connection failed"))

        val event = FeedImportCompleted(
            feedId = FeedId("f-test-feed"),
            routesProcessed = 50,
            variantsIdentified = 120,
            durationMillis = 5000
        )

        // When - should catch exception and log
        listener.onFeedImportCompleted(event)

        // Then - error is logged but doesn't propagate
    }
}
