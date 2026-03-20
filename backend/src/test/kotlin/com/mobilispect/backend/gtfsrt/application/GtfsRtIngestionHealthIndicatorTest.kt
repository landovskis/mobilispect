package com.mobilispect.backend.gtfsrt.application

import com.mobilispect.backend.feed.domain.model.Feed
import com.mobilispect.backend.feed.domain.model.ids.FeedId
import com.mobilispect.backend.feed.domain.repository.FeedRepository
import com.mobilispect.backend.feed.model.FeedSpecType
import com.mobilispect.backend.feed.model.FeedStatus
import com.mobilispect.backend.gtfsrt.infrastructure.FeedCircuitBreakerRegistry
import io.mockk.every
import io.mockk.mockk
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.boot.health.contributor.Status

class GtfsRtIngestionHealthIndicatorTest {

  private val feedRepository: FeedRepository = mockk()
  private val circuitBreakerRegistry = FeedCircuitBreakerRegistry()
  private lateinit var healthIndicator: GtfsRtIngestionHealthIndicator

  @BeforeEach
  fun setUp() {
    healthIndicator = GtfsRtIngestionHealthIndicator(feedRepository, circuitBreakerRegistry)
  }

  @Test
  fun `health returns UNKNOWN when no active feeds found`() {
    every { feedRepository.findByStatusAndRealtimeFeedUrlNotNull(FeedStatus.ACTIVE) } returns
      emptyList()

    val health = healthIndicator.health()

    assertEquals(Status.UNKNOWN, health.status)
    assertEquals(0, health.details["feeds"])
  }

  @Test
  fun `health returns UP when feeds exist and all circuit breakers closed`() {
    val feed = createFeed("feed-1")
    every { feedRepository.findByStatusAndRealtimeFeedUrlNotNull(FeedStatus.ACTIVE) } returns
      listOf(feed)

    val health = healthIndicator.health()

    assertEquals(Status.UP, health.status)
    assertEquals(1, health.details["feeds"])
    assertEquals(0, health.details["openCircuitBreakers"])
  }

  @Test
  fun `health returns DOWN when some circuit breakers are open`() {
    val feed = createFeed("feed-1")
    every { feedRepository.findByStatusAndRealtimeFeedUrlNotNull(FeedStatus.ACTIVE) } returns
      listOf(feed)

    // Force circuit breaker to open by recording repeated failures
    val cb = circuitBreakerRegistry.getOrCreate(FeedId("feed-1"))
    repeat(10) {
      cb.onError(
        0,
        java.util.concurrent.TimeUnit.MILLISECONDS,
        RuntimeException("simulated failure"),
      )
    }

    val health = healthIndicator.health()

    assertEquals(Status.DOWN, health.status)
    assertNotNull(health.details["openCircuitBreakers"])
    @Suppress("UNCHECKED_CAST") val openFeeds = health.details["openFeeds"] as List<String>
    assert(openFeeds.contains("feed-1"))
  }

  @Test
  fun `health details include feed count`() {
    val feeds = listOf(createFeed("feed-1"), createFeed("feed-2"), createFeed("feed-3"))
    every { feedRepository.findByStatusAndRealtimeFeedUrlNotNull(FeedStatus.ACTIVE) } returns feeds

    val health = healthIndicator.health()

    assertEquals(3, health.details["feeds"])
  }

  private fun createFeed(feedId: String) =
    Feed(
      feedId = FeedId(feedId),
      name = "Test Feed $feedId",
      specType = FeedSpecType.GTFS,
      downloadUrl = "https://example.com/$feedId.zip",
      realtimeFeedUrl = "https://example.com/$feedId/gtfsrt",
      status = FeedStatus.ACTIVE,
    )
}
