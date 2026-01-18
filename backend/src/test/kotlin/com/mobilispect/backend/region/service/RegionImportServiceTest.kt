package com.mobilispect.backend.region.service

import com.mobilispect.backend.feed.FeedApi
import com.mobilispect.backend.feed.domain.FeedImport
import com.mobilispect.backend.feed.domain.model.Feed
import com.mobilispect.backend.feed.domain.model.ids.FeedId
import com.mobilispect.backend.feed.model.FeedSpecType
import com.mobilispect.backend.feed.model.FeedStatus
import com.mobilispect.backend.feed.model.ImportTriggerType
import com.mobilispect.backend.feed.model.ids.ImportId
import com.mobilispect.backend.feed.service.RateLimitedJobLauncher
import com.mobilispect.backend.region.RegionId
import com.mobilispect.backend.region.data.repository.RegionImportRepository
import com.mobilispect.backend.region.domain.RegionImport
import com.mobilispect.backend.region.domain.RegionImportId
import com.mobilispect.backend.region.domain.RegionImportStatus
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import java.util.Optional
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.batch.core.job.Job
import org.springframework.batch.core.job.parameters.JobParameters
import org.springframework.core.task.TaskExecutor

class RegionImportServiceTest {
  private lateinit var feedApi: FeedApi
  private lateinit var regionImportRepository: RegionImportRepository
  private lateinit var jobLauncher: RateLimitedJobLauncher
  private lateinit var regionImportJob: Job
  private lateinit var taskExecutor: TaskExecutor
  private lateinit var service: RegionImportService

  @BeforeEach
  fun setUp() {
    feedApi = mockk()
    regionImportRepository = mockk()
    jobLauncher = mockk()
    regionImportJob = mockk()
    taskExecutor = TaskExecutor { runnable -> runnable.run() }

    service =
      RegionImportService(
        feedApi = feedApi,
        regionImportRepository = regionImportRepository,
        rateLimitedJobLauncher = jobLauncher,
        regionImportJob = regionImportJob,
        importLaunchExecutor = taskExecutor,
      )
  }

  @Test
  fun `returns completed response when no active feeds exist`() {
    val regionId = RegionId("r-empty")
    every { regionImportRepository.findActiveByRegionOnestopId(regionId.value) } returns
      Optional.empty()
    every { feedApi.findActiveFeedsByRegion(regionId) } returns emptyList()

    val response = service.import(regionId, ImportTriggerType.MANUAL)

    assertThat(response.status).isEqualTo(RegionImportStatus.COMPLETED)
    assertThat(response.totalFeeds).isZero()
    assertThat(response.regionImportId).isNull()
    verify(exactly = 0) { regionImportRepository.save(any()) }
  }

  @Test
  fun `returns existing import when one is active`() {
    val regionId = RegionId("r-active")
    val existingImport =
      RegionImport(
        id = RegionImportId.random(),
        regionOnestopId = regionId.value,
        triggerType = ImportTriggerType.MANUAL,
        status = RegionImportStatus.RUNNING,
        totalFeeds = 3,
      )

    every { regionImportRepository.findActiveByRegionOnestopId(regionId.value) } returns
      Optional.of(existingImport)

    val response = service.import(regionId, ImportTriggerType.MANUAL)

    assertThat(response.regionImportId).isEqualTo(existingImport.id.value.toString())
    assertThat(response.status).isEqualTo(existingImport.status)
    verify(exactly = 0) { regionImportRepository.save(any()) }
    verify(exactly = 0) { feedApi.findActiveFeedsByRegion(regionId) }
  }

  @Test
  fun `creates region import and launches job for active feeds`() {
    val regionId = RegionId("r-start")
    val feeds = listOf(feed("f-1", regionId), feed("f-2", regionId))
    val regionImportId = RegionImportId.random()
    val savedImport =
      RegionImport(
        id = regionImportId,
        regionOnestopId = regionId.value,
        triggerType = ImportTriggerType.MANUAL,
        status = RegionImportStatus.PENDING,
        totalFeeds = feeds.size,
      )

    every { regionImportRepository.findActiveByRegionOnestopId(regionId.value) } returns
      Optional.empty()
    every { feedApi.findActiveFeedsByRegion(regionId) } returns feeds
    every { regionImportRepository.save(any()) } returns savedImport
    every { jobLauncher.run(regionImportJob, any<JobParameters>()) } returns mockk()
    feeds.forEach { feed ->
      every { feedApi.import(feed.feedId, ImportTriggerType.MANUAL) } returns
        FeedImport(id = ImportId.random(), feedId = feed.feedId.value)
    }

    val response = service.import(regionId, ImportTriggerType.MANUAL)

    assertThat(response.regionImportId).isEqualTo(regionImportId.value.toString())
    assertThat(response.totalFeeds).isEqualTo(feeds.size)
    assertThat(response.status).isEqualTo(RegionImportStatus.PENDING)
    verify(exactly = 1) { jobLauncher.run(regionImportJob, any<JobParameters>()) }
  }

  private fun feed(feedId: String, regionId: RegionId): Feed {
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
