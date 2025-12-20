package com.mobilispect.backend.config

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.jsontype.BasicPolymorphicTypeValidator
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import org.slf4j.LoggerFactory
import org.springframework.cache.CacheManager
import org.springframework.cache.annotation.EnableCaching
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.data.redis.cache.RedisCacheConfiguration
import org.springframework.data.redis.cache.RedisCacheManager
import org.springframework.data.redis.connection.RedisConnectionFactory
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer
import org.springframework.data.redis.serializer.RedisSerializationContext
import org.springframework.data.redis.serializer.SerializationException
import org.springframework.data.redis.serializer.StringRedisSerializer
import java.time.Duration

/**
 * Redis configuration for transient progress data storage and caching.
 *
 * Task T009: Configure Redis for transient progress data
 * Task T096: Add Redis caching for agency and frequency queries
 *
 * Redis is used for:
 * 1. Ephemeral import progress data that doesn't need database persistence
 * 2. Caching of agency and frequency query results (T096)
 *
 * Progress data includes:
 * - Real-time import progress percentages
 * - Current processing step information
 * - Estimated time remaining
 * - Processing rates
 *
 * Cache data includes:
 * - Agency queries with 24-hour TTL
 * - Frequency calculations with 24-hour TTL
 *
 * Example Redis key structure:
 * - feed:import:progress:{importId} -> ImportProgress object
 * - agency-queries::{queryKey} -> Cached agency data
 * - frequency-queries::{queryKey} -> Cached frequency data
 */
@Configuration
@EnableCaching
class RedisConfiguration : org.springframework.cache.annotation.CachingConfigurer {

    private val logger = LoggerFactory.getLogger(RedisConfiguration::class.java)

    companion object {
        /**
         * Key prefix for all feed management Redis keys
         */
        const val KEY_PREFIX = "feed:import:progress:"

        /**
         * Default TTL for progress data (24 hours in seconds)
         */
        const val DEFAULT_TTL_SECONDS = 86400L

        /**
         * Cache version - increment this when Jackson serialization config changes
         * to automatically invalidate incompatible cached data.
         */
        private const val CACHE_VERSION = "v1"

        /**
         * Cache names for transit analysis queries (T096)
         */
        const val AGENCY_CACHE = "agency-queries::$CACHE_VERSION"
        const val FREQUENCY_CACHE = "frequency-queries::$CACHE_VERSION"

        /**
         * Cache TTL for agency queries (24 hours) - T096
         */
        val AGENCY_TTL: Duration = Duration.ofHours(24)

        /**
         * Cache TTL for frequency queries (1 hour) - T123
         */
        val FREQUENCY_TTL: Duration = Duration.ofHours(1)

        /**
         * Redis host configuration key
         */
        const val REDIS_HOST_PROPERTY = "spring.redis.host"

        /**
         * Redis port configuration key
         */
        const val REDIS_PORT_PROPERTY = "spring.redis.port"

        /**
         * Redis password configuration key
         */
        const val REDIS_PASSWORD_PROPERTY = "spring.redis.password"
    }

    /**
     * Builds Redis key for import progress data.
     */
    fun progressKey(importId: String): String = "$KEY_PREFIX$importId"

    /**
     * Configure cache manager with specific TTLs for different cache types.
     * Task T096: Agency and frequency queries cached for 24 hours.
     *
     * Uses a custom ObjectMapper that supports PageImpl serialization/deserialization.
     */
    @Bean
    fun cacheManager(connectionFactory: RedisConnectionFactory, pageJacksonModule: PageJacksonModule): CacheManager {
        // Create ObjectMapper with PageJacksonModule for proper Page serialization
        val objectMapper = ObjectMapper()
            .registerKotlinModule()
            .registerModule(pageJacksonModule)
            .activateDefaultTyping(
                BasicPolymorphicTypeValidator.builder()
                    .allowIfBaseType(Any::class.java)
                    .allowIfSubType(java.util.List::class.java)
                    .allowIfSubType(java.util.ArrayList::class.java)
                    .allowIfSubType(java.util.Collections::class.java)
                    .build(),
                ObjectMapper.DefaultTyping.NON_FINAL
            )

        val defaultConfig = RedisCacheConfiguration.defaultCacheConfig()
            .serializeKeysWith(
                RedisSerializationContext.SerializationPair.fromSerializer(StringRedisSerializer())
            )
            .serializeValuesWith(
                RedisSerializationContext.SerializationPair.fromSerializer(
                    GenericJackson2JsonRedisSerializer(objectMapper)
                )
            )
            .entryTtl(Duration.ofMinutes(5)) // Default 5-minute TTL
            .disableCachingNullValues() // Don't cache null values

        val cacheConfigurations = mapOf(
            AGENCY_CACHE to defaultConfig.entryTtl(AGENCY_TTL),
            FREQUENCY_CACHE to defaultConfig.entryTtl(FREQUENCY_TTL)
        )

        return RedisCacheManager.builder(connectionFactory)
            .cacheDefaults(defaultConfig)
            .withInitialCacheConfigurations(cacheConfigurations)
            .transactionAware() // Make cache operations participate in transactions
            .build()
    }

    /**
     * Error handler for Redis cache operations.
     * Logs serialization errors and allows the application to continue by falling back to direct queries.
     */
    override fun errorHandler(): org.springframework.cache.interceptor.CacheErrorHandler {
        return object : org.springframework.cache.interceptor.CacheErrorHandler {
            override fun handleCacheGetError(exception: RuntimeException, cache: org.springframework.cache.Cache, key: Any) {
                if (exception is SerializationException || exception.cause is SerializationException) {
                    logger.warn(
                        "Cache deserialization failed for cache '{}' and key '{}'. " +
                        "This may indicate a cache version mismatch. The cache entry will be evicted and regenerated. " +
                        "Error: {}",
                        cache.name,
                        key,
                        exception.message
                    )
                    // Evict the problematic entry so it will be regenerated
                    try {
                        cache.evict(key)
                    } catch (e: Exception) {
                        logger.error("Failed to evict cache entry: {}", e.message)
                    }
                } else {
                    logger.error("Cache get error for cache '{}' and key '{}': {}", cache.name, key, exception.message, exception)
                }
            }

            override fun handleCachePutError(exception: RuntimeException, cache: org.springframework.cache.Cache, key: Any, value: Any?) {
                logger.error("Cache put error for cache '{}' and key '{}': {}", cache.name, key, exception.message, exception)
            }

            override fun handleCacheEvictError(exception: RuntimeException, cache: org.springframework.cache.Cache, key: Any) {
                logger.error("Cache evict error for cache '{}' and key '{}': {}", cache.name, key, exception.message, exception)
            }

            override fun handleCacheClearError(exception: RuntimeException, cache: org.springframework.cache.Cache) {
                logger.error("Cache clear error for cache '{}': {}", cache.name, exception.message, exception)
            }
        }
    }
}
