package com.mobilispect.backend.gtfsrt.infrastructure

import com.mobilispect.backend.feed.domain.model.ids.FeedId
import com.mobilispect.backend.gtfsrt.domain.model.GtfsRtFeedState
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class InMemoryGtfsRtFeedStateRepositoryTest {

  @Test
  fun `findByFeedId returns null for unknown feed`() {
    val repository = InMemoryGtfsRtFeedStateRepository()

    val result = repository.findByFeedId(FeedId("f-unknown"))

    assertNull(result)
  }

  @Test
  fun `save persists state and findByFeedId retrieves it`() {
    val repository = InMemoryGtfsRtFeedStateRepository()
    val feedId = FeedId("f-test-feed")
    val state =
      GtfsRtFeedState(
        feedId = feedId,
        contentHash = "abc123",
        etag = "\"etag-value\"",
        lastModified = "Wed, 21 Oct 2024 07:28:00 GMT",
        gtfsRtTimestamp = 1700000000L,
        lastFetchedAt = Instant.now(),
        lastProcessedAt = Instant.now(),
      )

    val saved = repository.save(state)
    val retrieved = repository.findByFeedId(feedId)

    assertEquals(state, saved)
    assertNotNull(retrieved)
    assertEquals(state.contentHash, retrieved.contentHash)
    assertEquals(state.etag, retrieved.etag)
    assertEquals(state.gtfsRtTimestamp, retrieved.gtfsRtTimestamp)
  }

  @Test
  fun `save updates existing state`() {
    val repository = InMemoryGtfsRtFeedStateRepository()
    val feedId = FeedId("f-test-feed")
    val now = Instant.now()

    val state1 =
      GtfsRtFeedState(
        feedId = feedId,
        contentHash = "hash1",
        etag = null,
        lastModified = null,
        gtfsRtTimestamp = 1000L,
        lastFetchedAt = now,
        lastProcessedAt = now,
      )

    val state2 =
      GtfsRtFeedState(
        feedId = feedId,
        contentHash = "hash2",
        etag = "\"new-etag\"",
        lastModified = null,
        gtfsRtTimestamp = 2000L,
        lastFetchedAt = now.plusSeconds(30),
        lastProcessedAt = now.plusSeconds(30),
      )

    repository.save(state1)
    repository.save(state2)
    val retrieved = repository.findByFeedId(feedId)

    assertNotNull(retrieved)
    assertEquals("hash2", retrieved.contentHash)
    assertEquals(2000L, retrieved.gtfsRtTimestamp)
  }

  @Test
  fun `deleteByFeedId removes state`() {
    val repository = InMemoryGtfsRtFeedStateRepository()
    val feedId = FeedId("f-test-feed")
    val state =
      GtfsRtFeedState(
        feedId = feedId,
        contentHash = "abc",
        etag = null,
        lastModified = null,
        gtfsRtTimestamp = 1000L,
        lastFetchedAt = Instant.now(),
        lastProcessedAt = null,
      )

    repository.save(state)
    repository.deleteByFeedId(feedId)
    val result = repository.findByFeedId(feedId)

    assertNull(result)
  }

  @Test
  fun `clear removes all state`() {
    val repository = InMemoryGtfsRtFeedStateRepository()
    val now = Instant.now()

    repository.save(createState(FeedId("f-feed-1"), now))
    repository.save(createState(FeedId("f-feed-2"), now))
    repository.save(createState(FeedId("f-feed-3"), now))

    assertEquals(3, repository.size())

    repository.clear()

    assertEquals(0, repository.size())
  }

  private fun createState(feedId: FeedId, fetchedAt: Instant) =
    GtfsRtFeedState(
      feedId = feedId,
      contentHash = "hash-${feedId.value}",
      etag = null,
      lastModified = null,
      gtfsRtTimestamp = System.currentTimeMillis() / 1000,
      lastFetchedAt = fetchedAt,
      lastProcessedAt = null,
    )
}
