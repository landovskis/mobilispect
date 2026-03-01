package com.mobilispect.backend.gtfsrt.application

import com.mobilispect.backend.feed.domain.model.Feed
import com.mobilispect.backend.feed.domain.model.ids.FeedId
import com.mobilispect.backend.feed.domain.repository.FeedRepository
import com.mobilispect.backend.feed.model.FeedSpecType
import com.mobilispect.backend.feed.model.FeedStatus
import com.mobilispect.backend.gtfsrt.domain.model.GtfsRtFetchResult
import com.mobilispect.backend.gtfsrt.domain.model.UnchangedReason
import com.mobilispect.backend.gtfsrt.infrastructure.ParallelGtfsRtFetcher
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import java.time.Instant
import kotlinx.coroutines.flow.flowOf
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class GtfsRtIngestionServiceTest {

  private val feedRepository: FeedRepository = mockk(relaxed = true)
  private val fetcher: ParallelGtfsRtFetcher = mockk()
  private val processor: GtfsRtProcessingService = mockk()
  private val meterRegistry = SimpleMeterRegistry()
  private lateinit var ingestionService: GtfsRtIngestionService

  private val feedId = FeedId("test-feed")
  private val feed = createFeed(feedId)

  @BeforeEach
  fun setUp() {
    ingestionService = GtfsRtIngestionService(feedRepository, fetcher, processor, meterRegistry)
    every { feedRepository.findById(feedId) } returns feed
    every { feedRepository.save(any()) } answers { firstArg() }
  }

  @Test
  fun `ingest updates lastCheckedAt and lastUpdatedAt when new data is processed`() {
    val result = newDataResult(feedId)
    every { feedRepository.findByStatusAndRealtimeFeedUrlNotNull(FeedStatus.ACTIVE) } returns listOf(feed)
    coEvery { fetcher.fetchAllFeeds(listOf(feed)) } returns flowOf(result)
    coEvery { processor.process(result) } returns ProcessingOutcome.Processed(feedId, 3)

    val savedFeed = slot<Feed>()
    every { feedRepository.save(capture(savedFeed)) } answers { firstArg() }

    ingestionService.ingest()

    assertNotNull(savedFeed.captured.lastCheckedAt)
    assertNotNull(savedFeed.captured.lastUpdatedAt)
  }

  @Test
  fun `ingest updates only lastCheckedAt when data is unchanged`() {
    val result = GtfsRtFetchResult.Unchanged(feedId, UnchangedReason.HTTP_NOT_MODIFIED, Instant.now())
    every { feedRepository.findByStatusAndRealtimeFeedUrlNotNull(FeedStatus.ACTIVE) } returns listOf(feed)
    coEvery { fetcher.fetchAllFeeds(listOf(feed)) } returns flowOf(result)

    val savedFeed = slot<Feed>()
    every { feedRepository.save(capture(savedFeed)) } answers { firstArg() }

    ingestionService.ingest()

    assertNotNull(savedFeed.captured.lastCheckedAt)
    assertNull(savedFeed.captured.lastUpdatedAt)
  }

  @Test
  fun `ingest skips feed status update when feed not found`() {
    val result = newDataResult(feedId)
    every { feedRepository.findByStatusAndRealtimeFeedUrlNotNull(FeedStatus.ACTIVE) } returns listOf(feed)
    coEvery { fetcher.fetchAllFeeds(listOf(feed)) } returns flowOf(result)
    coEvery { processor.process(result) } returns ProcessingOutcome.Processed(feedId, 3)
    every { feedRepository.findById(feedId) } returns null

    // Should not throw even if feed is not found
    ingestionService.ingest()
  }

  private fun newDataResult(feedId: FeedId) = GtfsRtFetchResult.NewData(
    feedId = feedId,
    data = ByteArray(0),
    contentHash = "hash123",
    etag = null,
    lastModified = null,
    fetchedAt = Instant.now(),
  )

  private fun createFeed(feedId: FeedId) = Feed(
    feedId = com.mobilispect.backend.feed.domain.model.ids.FeedId(feedId.value),
    name = "Test Feed",
    specType = FeedSpecType.GTFS,
    downloadUrl = "https://example.com/gtfs.zip",
    realtimeFeedUrl = "https://example.com/gtfsrt",
    status = FeedStatus.ACTIVE,
  )
}
