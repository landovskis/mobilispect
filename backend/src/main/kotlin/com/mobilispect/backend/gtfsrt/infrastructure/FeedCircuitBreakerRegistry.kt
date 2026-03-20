package com.mobilispect.backend.gtfsrt.infrastructure

import com.mobilispect.backend.feed.domain.model.ids.FeedId
import io.github.resilience4j.circuitbreaker.CircuitBreaker
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry
import io.micrometer.core.instrument.MeterRegistry
import java.time.Duration
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import org.springframework.stereotype.Component

/**
 * Registry for per-feed circuit breakers.
 *
 * Each feed gets an independent circuit breaker to isolate failures. This ensures that one failing
 * feed doesn't prevent others from being fetched. Per ADR 0011.
 */
@Component
class FeedCircuitBreakerRegistry {

  private val registry: CircuitBreakerRegistry =
    CircuitBreakerRegistry.of(
      CircuitBreakerConfig.custom()
        .failureRateThreshold(50f)
        .waitDurationInOpenState(Duration.ofSeconds(30))
        .slidingWindowSize(10)
        .slidingWindowType(CircuitBreakerConfig.SlidingWindowType.COUNT_BASED)
        .permittedNumberOfCallsInHalfOpenState(3)
        .automaticTransitionFromOpenToHalfOpenEnabled(true)
        .build()
    )

  private val circuitBreakers = ConcurrentHashMap<FeedId, CircuitBreaker>()

  /**
   * Get or create a circuit breaker for a feed.
   *
   * @param feedId The feed identifier
   * @return The circuit breaker for this feed
   */
  fun getOrCreate(feedId: FeedId): CircuitBreaker =
    circuitBreakers.computeIfAbsent(feedId) { registry.circuitBreaker("feed_${feedId.value}") }

  /**
   * Get the current state of a feed's circuit breaker.
   *
   * @param feedId The feed identifier
   * @return The circuit breaker state, or null if no circuit breaker exists
   */
  fun getState(feedId: FeedId): CircuitBreaker.State? = circuitBreakers[feedId]?.state

  /**
   * Get all circuit breakers currently tracked.
   *
   * @return Map of feed IDs to their circuit breakers
   */
  fun getAll(): Map<FeedId, CircuitBreaker> = circuitBreakers.toMap()

  /**
   * Reset a feed's circuit breaker to closed state.
   *
   * @param feedId The feed identifier
   */
  fun reset(feedId: FeedId) {
    circuitBreakers[feedId]?.reset()
  }

  /**
   * Record the current state of all circuit breakers as gauges.
   *
   * State encoding: CLOSED=1.0, HALF_OPEN=0.5, OPEN=0.0
   *
   * @param meterRegistry The Micrometer registry to record gauges in
   */
  fun recordCircuitBreakerStates(meterRegistry: MeterRegistry) {
    circuitBreakers.forEach { (feedId, cb) ->
      val stateValue =
        when (cb.state) {
          CircuitBreaker.State.CLOSED -> 1.0
          CircuitBreaker.State.HALF_OPEN -> 0.5
          CircuitBreaker.State.OPEN -> 0.0
          else -> -1.0
        }
      val stateHolder = AtomicInteger(stateValue.toInt())
      meterRegistry.gauge(
        "gtfsrt.circuitbreaker.state",
        listOf(io.micrometer.core.instrument.Tag.of("feed_id", feedId.value)),
        stateHolder,
      ) { _ ->
        stateValue
      }
    }
  }
}
