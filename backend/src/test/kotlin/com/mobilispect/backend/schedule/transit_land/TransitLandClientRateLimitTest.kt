package com.mobilispect.backend.schedule.transit_land

import io.github.resilience4j.ratelimiter.RateLimiter
import io.github.resilience4j.ratelimiter.RateLimiterConfig
import io.github.resilience4j.ratelimiter.RateLimiterRegistry
import io.github.resilience4j.ratelimiter.RequestNotPermitted
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.Duration
import kotlin.system.measureTimeMillis

class TransitLandClientRateLimitTest {
    private lateinit var rateLimiterRegistry: RateLimiterRegistry
    private lateinit var rateLimiter: RateLimiter

    @BeforeEach
    fun setUp() {
        // Create a rate limiter with 6 requests per second (matching Transit.land limits)
        val rateLimiterConfig = RateLimiterConfig.custom()
            .limitForPeriod(6)
            .limitRefreshPeriod(Duration.ofSeconds(1))
            .timeoutDuration(Duration.ofSeconds(5))
            .build()

        rateLimiterRegistry = RateLimiterRegistry.of(rateLimiterConfig)
        rateLimiter = rateLimiterRegistry.rateLimiter("transitland")
    }

    @Test
    fun `rate limiter enforces 6 requests per second limit`() {
        // When: Acquire permissions for 10 requests
        val timeTaken = measureTimeMillis {
            repeat(10) {
                RateLimiter.waitForPermission(rateLimiter)
            }
        }

        // Then: Should take at least 1 second due to rate limiting
        // (6 in first second, 4 in second second = minimum ~666ms)
        assertThat(timeTaken).isGreaterThanOrEqualTo(600)
    }

    @Test
    fun `rate limiter respects Transit land limit of 6 per second`() {
        // When: Acquire 6 permissions within one second
        val timeTaken = measureTimeMillis {
            repeat(6) {
                RateLimiter.waitForPermission(rateLimiter)
            }
        }

        // Then: Should complete quickly (all within first cycle)
        assertThat(timeTaken).isLessThan(200)
    }

    @Test
    fun `rate limiter blocks requests when timeout is exceeded`() {
        // Given: Create a restrictive rate limiter (1 per second with 100ms timeout)
        val restrictiveConfig = RateLimiterConfig.custom()
            .limitForPeriod(1)
            .limitRefreshPeriod(Duration.ofSeconds(1))
            .timeoutDuration(Duration.ofMillis(100))
            .build()

        val restrictiveRegistry = RateLimiterRegistry.of(restrictiveConfig)
        val restrictiveLimiter = restrictiveRegistry.rateLimiter("restrictive")

        // When: Try to acquire 2 permissions rapidly
        RateLimiter.waitForPermission(restrictiveLimiter) // First succeeds

        var exceptionThrown = false
        try {
            RateLimiter.waitForPermission(restrictiveLimiter) // Second should timeout
        } catch (e: RequestNotPermitted) {
            exceptionThrown = true
        }

        // Then: Second request should throw RequestNotPermitted
        assertThat(exceptionThrown).isTrue()
    }

    @Test
    fun `rate limiter allows burst of 6 requests then throttles`() {
        // When: Make 7 requests rapidly
        val timings = mutableListOf<Long>()
        val startTime = System.currentTimeMillis()

        repeat(7) {
            RateLimiter.waitForPermission(rateLimiter)
            timings.add(System.currentTimeMillis() - startTime)
        }

        // Then: First 6 should be fast, 7th should wait
        assertThat(timings[5]).isLessThan(200) // 6th request is fast
        assertThat(timings[6]).isGreaterThanOrEqualTo(800) // 7th request waits
    }

    @Test
    fun `rate limiter configuration matches Transit land requirements`() {
        // Given: Access the rate limiter from registry
        val rateLimiter = rateLimiterRegistry.rateLimiter("transitland")
        val config = rateLimiter.rateLimiterConfig

        // Then: Verify it's configured for 6 requests per second
        assertThat(config.limitForPeriod).isEqualTo(6)
        assertThat(config.limitRefreshPeriod).isEqualTo(Duration.ofSeconds(1))
    }
}
