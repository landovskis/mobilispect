package com.mobilispect.backend.gtfsrt.application

import com.mobilispect.backend.feed.domain.repository.FeedRepository
import com.mobilispect.backend.feed.model.FeedStatus
import com.mobilispect.backend.gtfsrt.infrastructure.FeedCircuitBreakerRegistry
import io.github.resilience4j.circuitbreaker.CircuitBreaker
import org.springframework.boot.health.contributor.Health
import org.springframework.boot.health.contributor.HealthIndicator
import org.springframework.boot.health.contributor.Status
import org.springframework.stereotype.Component

/**
 * Health indicator for the GTFS-RT ingestion subsystem.
 *
 * Reports:
 * - UNKNOWN: no active feeds with realtime URLs found
 * - UP: feeds exist and all circuit breakers are closed
 * - DOWN: one or more feed circuit breakers are open (feed failures)
 */
@Component
class GtfsRtIngestionHealthIndicator(
  private val feedRepository: FeedRepository,
  private val circuitBreakerRegistry: FeedCircuitBreakerRegistry,
) : HealthIndicator {

  override fun health(): Health {
    val feeds = feedRepository.findByStatusAndRealtimeFeedUrlNotNull(FeedStatus.ACTIVE)

    if (feeds.isEmpty()) {
      return Health.unknown()
        .withDetail("feeds", 0)
        .withDetail("message", "No active GTFS-RT feeds found")
        .build()
    }

    val allCircuitBreakers = circuitBreakerRegistry.getAll()
    val openBreakers =
      allCircuitBreakers.filter { (_, cb) -> cb.state == CircuitBreaker.State.OPEN }

    return if (openBreakers.isEmpty()) {
      Health.up().withDetail("feeds", feeds.size).withDetail("openCircuitBreakers", 0).build()
    } else {
      Health.status(Status.DOWN)
        .withDetail("feeds", feeds.size)
        .withDetail("openCircuitBreakers", openBreakers.size)
        .withDetail("openFeeds", openBreakers.keys.map { it.value })
        .build()
    }
  }
}
