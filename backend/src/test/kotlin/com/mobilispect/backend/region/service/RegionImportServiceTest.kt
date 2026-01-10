package com.mobilispect.backend.region.service

import com.mobilispect.backend.feed.FeedApi
import com.mobilispect.backend.feed.domain.FeedImport
import com.mobilispect.backend.feed.domain.model.Feed
import com.mobilispect.backend.feed.domain.model.ids.FeedId
import com.mobilispect.backend.feed.events.FeedImportCompletedEvent
import com.mobilispect.backend.feed.events.FeedImportFailedEvent
import com.mobilispect.backend.feed.events.FeedImportStartedEvent
import com.mobilispect.backend.feed.events.FeedImportStepCompletedEvent
import com.mobilispect.backend.feed.events.FeedImportStepStartedEvent
import com.mobilispect.backend.feed.model.FeedSpecType
import com.mobilispect.backend.feed.model.FeedStatus
import com.mobilispect.backend.feed.model.ImportTriggerType
import com.mobilispect.backend.feed.model.ids.ImportId
import com.mobilispect.backend.region.RegionId
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.context.ApplicationEventPublisher

class RegionImportServiceTest {
  private lateinit var feedApi: FeedApi
  private lateinit var eventPublisher: ApplicationEventPublisher
  private lateinit var service: RegionImportService

  @BeforeEach
  fun setUp() {
    feedApi = mockk()
    eventPublisher = mockk(relaxed = true)
    service = RegionImportService(feedApi = feedApi, eventPublisher = eventPublisher)
  }

  @Test
  fun `onFeedImportStarted tracks started state`() {
    val feedId = FeedId("f-started")

    service.onFeedImportStarted(FeedImportStartedEvent(feedId))

    val state = service.getFeedImportState(feedId)
    assertThat(state).isNotNull
    assertThat(state?.feedId).isEqualTo(feedId)
    assertThat(state?.status).isEqualTo(RegionFeedImportStatus.STARTED)
    assertThat(state?.currentStep).isNull()
    assertThat(state?.errorMessage).isNull()
  }

  @Test
  fun `onFeedImportStepStarted updates step state`() {
    val feedId = FeedId("f-step-start")

    service.onFeedImportStarted(FeedImportStartedEvent(feedId))
    service.onFeedImportStepStarted(FeedImportStepStartedEvent(feedId, "routes"))

    val state = service.getFeedImportState(feedId)
    assertThat(state?.status).isEqualTo(RegionFeedImportStatus.IN_PROGRESS)
    assertThat(state?.currentStep).isEqualTo("routes")
    assertThat(state?.errorMessage).isNull()
  }

  @Test
  fun `onFeedImportStepCompleted keeps in progress state`() {
    val feedId = FeedId("f-step-completed")

    service.onFeedImportStarted(FeedImportStartedEvent(feedId))
    service.onFeedImportStepCompleted(FeedImportStepCompletedEvent(feedId, "stops"))

    val state = service.getFeedImportState(feedId)
    assertThat(state?.status).isEqualTo(RegionFeedImportStatus.IN_PROGRESS)
    assertThat(state?.currentStep).isEqualTo("stops")
  }

  @Test
  fun `onFeedImportCompleted marks completed and preserves last step`() {
    val feedId = FeedId("f-completed")
    primeRegionImport(RegionId("r-completed"), listOf(feed("f-completed")))

    service.onFeedImportStepStarted(FeedImportStepStartedEvent(feedId, "trips"))
    service.onFeedImportCompleted(FeedImportCompletedEvent(feedId))

    val state = service.getFeedImportState(feedId)
    assertThat(state?.status).isEqualTo(RegionFeedImportStatus.COMPLETED)
    assertThat(state?.currentStep).isEqualTo("trips")
    assertThat(state?.errorMessage).isNull()
  }

  @Test
  fun `onFeedImportFailed marks failed with error details`() {
    val feedId = FeedId("f-failed")
    primeRegionImport(RegionId("r-failed"), listOf(feed("f-failed")))

    service.onFeedImportFailed(FeedImportFailedEvent(feedId, "agency", "boom"))

    val state = service.getFeedImportState(feedId)
    assertThat(state?.status).isEqualTo(RegionFeedImportStatus.FAILED)
    assertThat(state?.currentStep).isEqualTo("agency")
    assertThat(state?.errorMessage).isEqualTo("boom")
  }

  @Test
  fun `onFeedImportCompleted publishes region completed when all feeds complete`() {
    val regionId = RegionId("r-all-complete")
    val feeds = listOf(feed("f-1", regionId), feed("f-2", regionId))
    primeRegionImport(regionId, feeds)

    service.onFeedImportCompleted(FeedImportCompletedEvent(feeds[0].feedId))
    service.onFeedImportCompleted(FeedImportCompletedEvent(feeds[1].feedId))

    verify(exactly = 1) { eventPublisher.publishEvent(RegionFeedsImportCompletedEvent(regionId)) }
    verify(exactly = 0) {
      eventPublisher.publishEvent(match { it is RegionFeedsImportFailedEvent })
    }
  }

  @Test
  fun `onFeedImportFailed publishes region failed when some feeds fail`() {
    val regionId = RegionId("r-partial-fail")
    val feeds = listOf(feed("f-1", regionId), feed("f-2", regionId))
    primeRegionImport(regionId, feeds)

    service.onFeedImportCompleted(FeedImportCompletedEvent(feeds[0].feedId))
    service.onFeedImportFailed(FeedImportFailedEvent(feeds[1].feedId, "stops", "boom"))

    verify(exactly = 1) {
      eventPublisher.publishEvent(match { it is RegionFeedsImportFailedEvent })
    }
    verify(exactly = 0) { eventPublisher.publishEvent(RegionFeedsImportCompletedEvent(regionId)) }
  }

  @Test
  fun `onFeedImportCompleted does not publish when other feeds still in progress`() {
    val regionId = RegionId("r-in-progress")
    val feeds = listOf(feed("f-1", regionId), feed("f-2", regionId))
    primeRegionImport(regionId, feeds)

    service.onFeedImportStarted(FeedImportStartedEvent(feeds[1].feedId))
    service.onFeedImportCompleted(FeedImportCompletedEvent(feeds[0].feedId))

    verify(exactly = 0) { eventPublisher.publishEvent(RegionFeedsImportCompletedEvent(regionId)) }
    verify(exactly = 0) {
      eventPublisher.publishEvent(match { it is RegionFeedsImportFailedEvent })
    }
  }

  @Test
  fun `import only imports ACTIVE feeds, not INACTIVE or ERROR feeds`() {
    val regionId = RegionId("r-mixed-status")
    val activeFeeds = listOf(feed("f-active-1", regionId), feed("f-active-2", regionId))

    // Mock to return only active feeds (simulating the repository filter)
    every { feedApi.findActiveFeedsByRegion(regionId) } returns activeFeeds
    activeFeeds.forEach { feed ->
      every { feedApi.import(feed.feedId, ImportTriggerType.MANUAL) } returns
        FeedImport(id = ImportId.random(), feedId = feed.feedId.value)
    }

    val response = service.import(regionId, ImportTriggerType.MANUAL)

    // Verify only active feeds were imported
    assertThat(response.totalFeeds).isEqualTo(2)
    assertThat(response.startedCount).isEqualTo(2)
    assertThat(response.results).hasSize(2)
    assertThat(response.results.map { it.feedOnestopId }).containsExactlyInAnyOrder(
      "f-active-1",
      "f-active-2"
    )

    // Verify findActiveFeedsByRegion was called, not findFeedsByRegion
    verify(exactly = 1) { feedApi.findActiveFeedsByRegion(regionId) }
  }

  private fun primeRegionImport(regionId: RegionId, feeds: List<Feed>) {
    every { feedApi.findActiveFeedsByRegion(regionId) } returns feeds
    feeds.forEach { feed ->
      every { feedApi.import(feed.feedId, ImportTriggerType.MANUAL) } returns
        FeedImport(id = ImportId.random(), feedId = feed.feedId.value)
    }
    service.import(regionId, ImportTriggerType.MANUAL)
  }

  private fun feed(feedId: String, regionId: RegionId = RegionId("r-default")): Feed {
    return Feed(
      feedId = FeedId(feedId),
      name = "Feed $feedId",
      specType = FeedSpecType.GTFS,
      downloadUrl = "https://example.com/$feedId.zip",
      status = FeedStatus.ACTIVE,
      regionIds = setOf(regionId),
    )
  }
}
