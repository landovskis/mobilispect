package com.mobilispect.backend.gtfsrt.infrastructure

import com.mobilispect.backend.feed.domain.model.ids.FeedId
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import kotlin.test.assertEquals
import org.junit.jupiter.api.Test

class FeedCircuitBreakerMetricsTest {

  private val meterRegistry = SimpleMeterRegistry()
  private val registry = FeedCircuitBreakerRegistry()

  @Test
  fun `recordCircuitBreakerStates records CLOSED state as 1`() {
    val feedId = FeedId("feed-1")
    registry.getOrCreate(feedId)

    registry.recordCircuitBreakerStates(meterRegistry)

    val gauge = meterRegistry.find("gtfsrt.circuitbreaker.state").tag("feed_id", "feed-1").gauge()
    assertEquals(1.0, gauge?.value())
  }

  @Test
  fun `recordCircuitBreakerStates records OPEN state as 0`() {
    val feedId = FeedId("feed-2")
    val cb = registry.getOrCreate(feedId)
    repeat(10) {
      cb.onError(0, java.util.concurrent.TimeUnit.MILLISECONDS, RuntimeException("failure"))
    }

    registry.recordCircuitBreakerStates(meterRegistry)

    val gauge = meterRegistry.find("gtfsrt.circuitbreaker.state").tag("feed_id", "feed-2").gauge()
    assertEquals(0.0, gauge?.value())
  }

  @Test
  fun `recordCircuitBreakerStates records nothing when no circuit breakers exist`() {
    registry.recordCircuitBreakerStates(meterRegistry)

    val gauges = meterRegistry.find("gtfsrt.circuitbreaker.state").gauges()
    assertEquals(0, gauges.size)
  }
}
