package com.mobilispect.backend.region.domain

import com.mobilispect.backend.feed.model.ImportTriggerType
import java.util.UUID
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class RegionImportTest {

  @Test
  fun `new region import has pending status and zero counts`() {
    val regionImport = createRegionImport()

    assertThat(regionImport.status).isEqualTo(RegionImportStatus.PENDING)
    assertThat(regionImport.totalFeeds).isEqualTo(0)
    assertThat(regionImport.startedCount).isEqualTo(0)
    assertThat(regionImport.completedCount).isEqualTo(0)
    assertThat(regionImport.failedCount).isEqualTo(0)
    assertThat(regionImport.skippedCount).isEqualTo(0)
    assertThat(regionImport.startedAt).isNull()
    assertThat(regionImport.completedAt).isNull()
  }

  @Test
  fun `start sets status to running and sets start time`() {
    val regionImport = createRegionImport()

    regionImport.start(jobExecutionId = 123L)

    assertThat(regionImport.status).isEqualTo(RegionImportStatus.RUNNING)
    assertThat(regionImport.startedAt).isNotNull
    assertThat(regionImport.parentJobExecutionId).isEqualTo(123L)
  }

  @Test
  fun `markFeedStarted increments started count`() {
    val regionImport = createRegionImport(totalFeeds = 3)
    regionImport.start()

    regionImport.markFeedStarted()
    regionImport.markFeedStarted()

    assertThat(regionImport.startedCount).isEqualTo(2)
  }

  @Test
  fun `all feeds completed results in COMPLETED status`() {
    val regionImport = createRegionImport(totalFeeds = 3)
    regionImport.start()

    regionImport.markFeedCompleted()
    regionImport.markFeedCompleted()
    regionImport.markFeedCompleted()

    assertThat(regionImport.status).isEqualTo(RegionImportStatus.COMPLETED)
    assertThat(regionImport.completedAt).isNotNull
    assertThat(regionImport.completedCount).isEqualTo(3)
    assertThat(regionImport.failedCount).isEqualTo(0)
  }

  @Test
  fun `some feeds failed results in PARTIAL_SUCCESS status`() {
    val regionImport = createRegionImport(totalFeeds = 3)
    regionImport.start()

    regionImport.markFeedCompleted()
    regionImport.markFeedCompleted()
    regionImport.markFeedFailed()

    assertThat(regionImport.status).isEqualTo(RegionImportStatus.PARTIAL_SUCCESS)
    assertThat(regionImport.completedAt).isNotNull
    assertThat(regionImport.completedCount).isEqualTo(2)
    assertThat(regionImport.failedCount).isEqualTo(1)
  }

  @Test
  fun `all feeds failed results in FAILED status`() {
    val regionImport = createRegionImport(totalFeeds = 2)
    regionImport.start()

    regionImport.markFeedFailed()
    regionImport.markFeedFailed()

    assertThat(regionImport.status).isEqualTo(RegionImportStatus.FAILED)
    assertThat(regionImport.completedAt).isNotNull
    assertThat(regionImport.completedCount).isEqualTo(0)
    assertThat(regionImport.failedCount).isEqualTo(2)
  }

  @Test
  fun `skipped feeds count toward completion`() {
    val regionImport = createRegionImport(totalFeeds = 3)
    regionImport.start()

    regionImport.markFeedCompleted()
    regionImport.markFeedSkipped()
    regionImport.markFeedSkipped()

    assertThat(regionImport.status).isEqualTo(RegionImportStatus.COMPLETED)
    assertThat(regionImport.completedAt).isNotNull
    assertThat(regionImport.completedCount).isEqualTo(1)
    assertThat(regionImport.skippedCount).isEqualTo(2)
  }

  @Test
  fun `fail sets FAILED status with error message`() {
    val regionImport = createRegionImport(totalFeeds = 2)
    regionImport.start()

    regionImport.fail("Something went wrong")

    assertThat(regionImport.status).isEqualTo(RegionImportStatus.FAILED)
    assertThat(regionImport.completedAt).isNotNull
    assertThat(regionImport.errorMessage).isEqualTo("Something went wrong")
  }

  @Test
  fun `cancel sets CANCELLED status`() {
    val regionImport = createRegionImport(totalFeeds = 2)
    regionImport.start()

    regionImport.cancel()

    assertThat(regionImport.status).isEqualTo(RegionImportStatus.CANCELLED)
    assertThat(regionImport.completedAt).isNotNull
  }

  @Test
  fun `addFeed creates junction entity with correct properties`() {
    val regionImport = createRegionImport(totalFeeds = 1)
    val feedImportId = UUID.randomUUID()

    val feed = regionImport.addFeed(feedImportId, sequenceNumber = 0)

    assertThat(regionImport.feeds).hasSize(1)
    assertThat(feed.feedImportId).isEqualTo(feedImportId)
    assertThat(feed.sequenceNumber).isEqualTo(0)
    assertThat(feed.regionImport).isEqualTo(regionImport)
  }

  @Test
  fun `multiple feeds can be added with sequence numbers`() {
    val regionImport = createRegionImport(totalFeeds = 3)

    regionImport.addFeed(UUID.randomUUID(), sequenceNumber = 0)
    regionImport.addFeed(UUID.randomUUID(), sequenceNumber = 1)
    regionImport.addFeed(UUID.randomUUID(), sequenceNumber = 2)

    assertThat(regionImport.feeds).hasSize(3)
    assertThat(regionImport.feeds.map { it.sequenceNumber }).containsExactly(0, 1, 2)
  }

  private fun createRegionImport(totalFeeds: Int = 0): RegionImport {
    return RegionImport(
      id = RegionImportId.random(),
      regionOnestopId = "r-test-region",
      triggerType = ImportTriggerType.MANUAL,
      totalFeeds = totalFeeds,
    )
  }
}
