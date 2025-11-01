package com.mobilispect.backend.config

import org.springframework.context.annotation.Configuration

/**
 * Redis configuration for transient progress data storage.
 *
 * Task T009: Configure Redis for transient progress data
 *
 * Redis is used to store ephemeral import progress data that doesn't need to be persisted
 * to the database. This includes:
 * - Real-time import progress percentages
 * - Current processing step information
 * - Estimated time remaining
 * - Processing rates
 *
 * This data is stored in Redis with TTL (Time To Live) expiration and is automatically
 * cleaned up after imports complete. The ProgressTrackingService uses this configuration
 * to manage progress data.
 *
 * Note: This is a placeholder configuration that documents the Redis requirements.
 * Actual Redis integration requires:
 * 1. RedisTemplate bean configuration
 * 2. RedisConnectionFactory configuration
 * 3. Jackson2JsonRedisSerializer for progress objects
 * 4. Key prefix configuration
 * 5. TTL configuration for automatic cleanup
 *
 * The ProgressTrackingService in the websocket package currently manages progress data.
 * Full Redis integration should be coordinated with that implementation to ensure
 * consistency and proper data flow.
 *
 * Example Redis key structure:
 * - feed:import:progress:{importId} -> ImportProgress object
 * - TTL: 24 hours (configurable)
 */
@Configuration
class RedisConfiguration {

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
}
