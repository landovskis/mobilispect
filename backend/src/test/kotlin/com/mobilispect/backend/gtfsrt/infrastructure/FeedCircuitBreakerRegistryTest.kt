package com.mobilispect.backend.gtfsrt.infrastructure

import com.mobilispect.backend.feed.domain.model.ids.FeedId
import io.github.resilience4j.circuitbreaker.CircuitBreaker
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame

class FeedCircuitBreakerRegistryTest {

  @Test
  fun `getOrCreate returns new circuit breaker for unknown feed`() {
    val registry = FeedCircuitBreakerRegistry()
    val feedId = FeedId("f-test-feed-1")

    val breaker = registry.getOrCreate(feedId)

    assertNotNull(breaker)
    assertEquals(CircuitBreaker.State.CLOSED, breaker.state)
  }

  @Test
  fun `getOrCreate returns same circuit breaker for same feed`() {
    val registry = FeedCircuitBreakerRegistry()
    val feedId = FeedId("f-test-feed-2")

    val breaker1 = registry.getOrCreate(feedId)
    val breaker2 = registry.getOrCreate(feedId)

    assertSame(breaker1, breaker2)
  }

  @Test
  fun `getOrCreate returns different circuit breakers for different feeds`() {
    val registry = FeedCircuitBreakerRegistry()
    val feedId1 = FeedId("f-test-feed-a")
    val feedId2 = FeedId("f-test-feed-b")

    val breaker1 = registry.getOrCreate(feedId1)
    val breaker2 = registry.getOrCreate(feedId2)

    assertNotNull(breaker1)
    assertNotNull(breaker2)
    assert(breaker1 !== breaker2)
  }

  @Test
  fun `getState returns null for unknown feed`() {
    val registry = FeedCircuitBreakerRegistry()
    val feedId = FeedId("f-unknown-feed")

    val state = registry.getState(feedId)

    assertNull(state)
  }

  @Test
  fun `getState returns state after circuit breaker created`() {
    val registry = FeedCircuitBreakerRegistry()
    val feedId = FeedId("f-test-feed-3")

    registry.getOrCreate(feedId)
    val state = registry.getState(feedId)

    assertEquals(CircuitBreaker.State.CLOSED, state)
  }

  @Test
  fun `getAll returns all tracked circuit breakers`() {
    val registry = FeedCircuitBreakerRegistry()
    val feedId1 = FeedId("f-feed-1")
    val feedId2 = FeedId("f-feed-2")

    registry.getOrCreate(feedId1)
    registry.getOrCreate(feedId2)

    val all = registry.getAll()

    assertEquals(2, all.size)
    assert(all.containsKey(feedId1))
    assert(all.containsKey(feedId2))
  }

  @Test
  fun `reset resets circuit breaker to closed state`() {
    val registry = FeedCircuitBreakerRegistry()
    val feedId = FeedId("f-test-feed-4")

    val breaker = registry.getOrCreate(feedId)
    // Simulate failures to open the circuit
    repeat(10) {
      breaker.onError(0, java.util.concurrent.TimeUnit.MILLISECONDS, RuntimeException("test"))
    }

    // Circuit should be open after enough failures
    registry.reset(feedId)

    assertEquals(CircuitBreaker.State.CLOSED, breaker.state)
  }
}
