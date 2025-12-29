package com.mobilispect.backend.feed.controller

import com.mobilispect.backend.feed.domain.FeedImport
import com.mobilispect.backend.feed.domain.model.ids.FeedId
import com.mobilispect.backend.feed.model.FeedEntity
import com.mobilispect.backend.feed.model.FeedSpecType
import com.mobilispect.backend.feed.model.FeedStatus
import com.mobilispect.backend.feed.model.ImportStatus
import com.mobilispect.backend.feed.model.ImportTriggerType
import com.mobilispect.backend.feed.model.ids.ImportId
import com.mobilispect.backend.feed.repository.FeedImportRepository
import com.mobilispect.backend.feed.repository.FeedRepository
import com.mobilispect.backend.feed.service.FeedImportProgressService
import com.mobilispect.backend.feed.service.FeedImportService
import com.mobilispect.backend.feed.service.ImportHistoryService
import com.mobilispect.backend.feed.service.ImportStatistics
import com.mobilispect.backend.websocket.ImportProgress
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import java.time.Instant
import java.util.Optional
import java.util.UUID
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.http.HttpStatus
import org.springframework.web.server.ResponseStatusException

class FeedImportControllerTest {

  private lateinit var feedImportService: FeedImportService
  private lateinit var feedImportRepository: FeedImportRepository
  private lateinit var feedRepository: FeedRepository
  private lateinit var importProgressService: FeedImportProgressService
  private lateinit var importHistoryService: ImportHistoryService
  private lateinit var controller: FeedImportController

  private val feedId = "f-test-feed"
  private val importId = UUID.randomUUID()
  private val createdAt = Instant.parse("2025-01-15T12:00:00Z")

  @BeforeEach
  fun setUp() {
    feedImportService = mockk()
    feedImportRepository = mockk()
    feedRepository = mockk()
    importProgressService = mockk()
    importHistoryService = mockk()

    controller =
      FeedImportController(
        feedImportService,
        feedImportRepository,
        feedRepository,
        importProgressService,
        importHistoryService,
      )
  }

  // ========== Import Operations ==========

  @Test
  fun `startImport successfully starts import and returns response`() {
    every { feedImportService.startImport(FeedId(feedId), ImportTriggerType.MANUAL) } returns
      ImportId(importId)

    val result = controller.startImport(feedId)

    assertThat(result.importId).isEqualTo(importId.toString())
    assertThat(result.feedId).isEqualTo(feedId)
    verify { feedImportService.startImport(FeedId(feedId), ImportTriggerType.MANUAL) }
  }

  @Test
  fun `startImport throws BAD_REQUEST when service throws IllegalArgumentException`() {
    every { feedImportService.startImport(FeedId(feedId), ImportTriggerType.MANUAL) } throws
      IllegalArgumentException("Feed not found")

    assertThatThrownBy { controller.startImport(feedId) }
      .isInstanceOf(ResponseStatusException::class.java)
      .hasFieldOrPropertyWithValue("status", HttpStatus.BAD_REQUEST)
  }

  @Test
  fun `startImport throws INTERNAL_SERVER_ERROR on unexpected exception`() {
    every { feedImportService.startImport(FeedId(feedId), ImportTriggerType.MANUAL) } throws
      RuntimeException("Database error")

    assertThatThrownBy { controller.startImport(feedId) }
      .isInstanceOf(ResponseStatusException::class.java)
      .hasFieldOrPropertyWithValue("status", HttpStatus.INTERNAL_SERVER_ERROR)
  }

  @Test
  fun `cancelImport successfully cancels import and returns updated import`() {
    val feedImport = createFeedImport(ImportStatus.CANCELLED)

    every { feedImportService.cancelImport(ImportId(importId)) } returns Unit
    every { feedImportRepository.findByImportId(ImportId(importId)) } returns
      Optional.of(feedImport)

    val result = controller.cancelImport(importId.toString())

    assertThat(result.id).isEqualTo(importId.toString())
    assertThat(result.status.name).isEqualTo("CANCELLED")
    verify { feedImportService.cancelImport(ImportId(importId)) }
  }

  @Test
  fun `cancelImport throws NOT_FOUND when import does not exist`() {
    every { feedImportService.cancelImport(ImportId(importId)) } returns Unit
    every { feedImportRepository.findByImportId(ImportId(importId)) } returns Optional.empty()

    assertThatThrownBy { controller.cancelImport(importId.toString()) }
      .isInstanceOf(ResponseStatusException::class.java)
      .hasFieldOrPropertyWithValue("status", HttpStatus.NOT_FOUND)
  }

  @Test
  fun `cancelImport throws NOT_FOUND for invalid UUID`() {
    assertThatThrownBy { controller.cancelImport("invalid-uuid") }
      .isInstanceOf(ResponseStatusException::class.java)
      .hasFieldOrPropertyWithValue("status", HttpStatus.NOT_FOUND)
  }

  // ========== Active Import Tracking ==========

  @Test
  fun `getActiveImports returns list of active imports with progress`() {
    val runningImport = createFeedImport(ImportStatus.RUNNING)
    val pendingImport = createFeedImport(ImportStatus.PENDING)
    val feed = createFeed()
    val progress =
      ImportProgress(
        importId = ImportId(importId),
        feedId = FeedId(feedId),
        currentStep = "Processing routes",
      )

    every { feedImportRepository.findAllByStatusIn(any()) } returns
      listOf(runningImport, pendingImport)
    every { importProgressService.getProgress(ImportId(importId)) } returns progress
    every { feedRepository.findByFeedOnestopId(feedId) } returns Optional.of(feed)

    val result = controller.getActiveImports()

    assertThat(result.total).isEqualTo(2)
    assertThat(result.imports).hasSize(2)
    assertThat(result.imports.first().progress?.currentStep).isEqualTo("Processing routes")
  }

  @Test
  fun `getActiveImports returns empty list when no active imports`() {
    every { feedImportRepository.findAllByStatusIn(any()) } returns emptyList()

    val result = controller.getActiveImports()

    assertThat(result.total).isEqualTo(0)
    assertThat(result.imports).isEmpty()
  }

  @Test
  fun `getImportProgress returns progress from service when available`() {
    val progress =
      ImportProgress(
        importId = ImportId(importId),
        feedId = FeedId(feedId),
        currentStep = "Calculating frequencies",
      )

    every { importProgressService.getProgress(ImportId(importId)) } returns progress

    val result = controller.getImportProgress(importId.toString())

    assertThat(result.currentStep).isEqualTo("Calculating frequencies")
  }

  @Test
  fun `getImportProgress returns completed status for completed import`() {
    val completedImport = createFeedImport(ImportStatus.COMPLETED)

    every { importProgressService.getProgress(ImportId(importId)) } returns null
    every { feedImportRepository.findByImportId(ImportId(importId)) } returns
      Optional.of(completedImport)

    val result = controller.getImportProgress(importId.toString())

    assertThat(result.progressPercentage).isEqualTo(100)
    assertThat(result.currentStep).isEqualTo("Completed")
    assertThat(result.currentStepNumber).isEqualTo(8)
  }

  @Test
  fun `getImportProgress returns failed status for failed import`() {
    val failedImport = createFeedImport(ImportStatus.FAILED)

    every { importProgressService.getProgress(ImportId(importId)) } returns null
    every { feedImportRepository.findByImportId(ImportId(importId)) } returns
      Optional.of(failedImport)

    val result = controller.getImportProgress(importId.toString())

    assertThat(result.progressPercentage).isEqualTo(0)
    assertThat(result.currentStep).isEqualTo("Failed")
  }

  @Test
  fun `getImportProgress throws NOT_FOUND for invalid UUID`() {
    assertThatThrownBy { controller.getImportProgress("invalid-uuid") }
      .isInstanceOf(ResponseStatusException::class.java)
      .hasFieldOrPropertyWithValue("status", HttpStatus.NOT_FOUND)
  }

  // ========== Import Details ==========

  @Test
  fun `getImport returns detailed import information with progress`() {
    val feedImport = createFeedImport(ImportStatus.RUNNING)
    val feed = createFeed()
    val progress =
      ImportProgress(
        importId = ImportId(importId),
        feedId = FeedId(feedId),
        currentStep = "Processing routes",
      )

    every { feedImportRepository.findByImportId(ImportId(importId)) } returns
      Optional.of(feedImport)
    every { feedRepository.findByFeedOnestopId(feedId) } returns Optional.of(feed)
    every { importProgressService.getProgress(ImportId(importId)) } returns progress

    val result = controller.getImport(importId.toString())

    assertThat(result.id).isEqualTo(importId.toString())
    assertThat(result.feedOnestopId).isEqualTo(feedId)
    assertThat(result.feedName).isEqualTo("Test Feed")
    assertThat(result.status.name).isEqualTo("RUNNING")
    assertThat(result.progress?.currentStep).isEqualTo("Processing routes")
  }

  @Test
  fun `getImport throws NOT_FOUND when import does not exist`() {
    every { feedImportRepository.findByImportId(ImportId(importId)) } returns Optional.empty()

    assertThatThrownBy { controller.getImport(importId.toString()) }
      .isInstanceOf(ResponseStatusException::class.java)
      .hasFieldOrPropertyWithValue("status", HttpStatus.NOT_FOUND)
  }

  @Test
  fun `getImport throws INTERNAL_SERVER_ERROR when feed is missing`() {
    val feedImport = createFeedImport(ImportStatus.RUNNING)

    every { feedImportRepository.findByImportId(ImportId(importId)) } returns
      Optional.of(feedImport)
    every { feedRepository.findByFeedOnestopId(feedId) } returns Optional.empty()

    assertThatThrownBy { controller.getImport(importId.toString()) }
      .isInstanceOf(ResponseStatusException::class.java)
      .hasFieldOrPropertyWithValue("status", HttpStatus.INTERNAL_SERVER_ERROR)
  }

  // ========== Import History Lists ==========

  @Test
  fun `listImports returns paged imports without filters`() {
    val feedImport = createFeedImport(ImportStatus.COMPLETED)
    val pageable = PageRequest.of(0, 20, Sort.by(Sort.Direction.DESC, "startedAt", "createdAt"))
    val page = PageImpl(listOf(feedImport), pageable, 1)

    every { feedImportRepository.findAll(pageable) } returns page

    val result = controller.listImports(0, 20, null, null)

    assertThat(result.imports).hasSize(1)
    assertThat(result.page.totalElements).isEqualTo(1)
    assertThat(result.page.totalPages).isEqualTo(1)
  }

  @Test
  fun `listImports filters by status`() {
    val feedImport = createFeedImport(ImportStatus.COMPLETED)
    val pageable = PageRequest.of(0, 20, Sort.by(Sort.Direction.DESC, "startedAt", "createdAt"))
    val page = PageImpl(listOf(feedImport), pageable, 1)

    every {
      feedImportRepository.findAllByStatusIn(listOf(ImportStatus.COMPLETED), pageable)
    } returns page

    val result =
      controller.listImports(
        0,
        20,
        com.mobilispect.backend.feed.domain.ImportStatus.COMPLETED,
        null,
      )

    assertThat(result.imports).hasSize(1)
    assertThat(result.imports.first().status.name).isEqualTo("COMPLETED")
  }

  @Test
  fun `listImports filters by trigger type`() {
    val feedImport = createFeedImport(ImportStatus.COMPLETED)
    val pageable = PageRequest.of(0, 20, Sort.by(Sort.Direction.DESC, "startedAt", "createdAt"))
    val page = PageImpl(listOf(feedImport), pageable, 1)

    every {
      feedImportRepository.findAllByTriggerTypeIn(listOf(ImportTriggerType.MANUAL), pageable)
    } returns page

    val result =
      controller.listImports(0, 20, null, com.mobilispect.backend.feed.domain.TriggerType.MANUAL)

    assertThat(result.imports).hasSize(1)
  }

  @Test
  fun `listImports filters by both status and trigger type`() {
    val feedImport = createFeedImport(ImportStatus.COMPLETED)
    val pageable = PageRequest.of(0, 20, Sort.by(Sort.Direction.DESC, "startedAt", "createdAt"))
    val page = PageImpl(listOf(feedImport), pageable, 1)

    every {
      feedImportRepository.findAllByStatusInAndTriggerTypeIn(
        listOf(ImportStatus.COMPLETED),
        listOf(ImportTriggerType.MANUAL),
        pageable,
      )
    } returns page

    val result =
      controller.listImports(
        0,
        20,
        com.mobilispect.backend.feed.domain.ImportStatus.COMPLETED,
        com.mobilispect.backend.feed.domain.TriggerType.MANUAL,
      )

    assertThat(result.imports).hasSize(1)
  }

  @Test
  fun `listImportsForFeed returns imports for specific feed`() {
    val feedImport = createFeedImport(ImportStatus.COMPLETED)
    val pageable = PageRequest.of(0, 20, Sort.by(Sort.Direction.DESC, "startedAt", "createdAt"))
    val page = PageImpl(listOf(feedImport), pageable, 1)

    every { feedImportRepository.findAllByFeedIdOrderByStartedAtDesc(feedId, pageable) } returns
      page

    val result = controller.listImportsForFeed(feedId, 0, 20, null)

    assertThat(result.imports).hasSize(1)
    assertThat(result.imports.first().feedOnestopId).isEqualTo(feedId)
  }

  @Test
  fun `listImportsForFeed filters by status`() {
    val feedImport = createFeedImport(ImportStatus.COMPLETED)
    val pageable = PageRequest.of(0, 20, Sort.by(Sort.Direction.DESC, "startedAt", "createdAt"))
    val page = PageImpl(listOf(feedImport), pageable, 1)

    every {
      feedImportRepository.findAllByFeedIdAndStatusInOrderByStartedAtDesc(
        feedId,
        listOf(ImportStatus.COMPLETED),
        pageable,
      )
    } returns page

    val result =
      controller.listImportsForFeed(
        feedId,
        0,
        20,
        com.mobilispect.backend.feed.domain.ImportStatus.COMPLETED,
      )

    assertThat(result.imports).hasSize(1)
    assertThat(result.imports.first().status.name).isEqualTo("COMPLETED")
  }

  @Test
  fun `listImportsForRegion returns imports for specific region`() {
    val regionId = "r-test-region"
    val feedImport = createFeedImport(ImportStatus.COMPLETED)
    val pageable = PageRequest.of(0, 20, Sort.by(Sort.Direction.DESC, "startedAt"))
    val page = PageImpl(listOf(feedImport), pageable, 1)

    every { importHistoryService.getRegionImportHistory(regionId, null, pageable) } returns page

    val result = controller.listImportsForRegion(regionId, 0, 20, null)

    assertThat(result.imports).hasSize(1)
    assertThat(result.page.totalElements).isEqualTo(1)
  }

  @Test
  fun `listImportsForRegion filters by status`() {
    val regionId = "r-test-region"
    val feedImport = createFeedImport(ImportStatus.COMPLETED)
    val pageable = PageRequest.of(0, 20, Sort.by(Sort.Direction.DESC, "startedAt"))
    val page = PageImpl(listOf(feedImport), pageable, 1)

    every {
      importHistoryService.getRegionImportHistory(regionId, ImportStatus.COMPLETED, pageable)
    } returns page

    val result =
      controller.listImportsForRegion(
        regionId,
        0,
        20,
        com.mobilispect.backend.feed.domain.ImportStatus.COMPLETED,
      )

    assertThat(result.imports).hasSize(1)
  }

  // ========== Statistics ==========

  @Test
  fun `getStatistics returns import statistics`() {
    val stats =
      ImportStatistics(
        totalImports = 100,
        completedImports = 80,
        failedImports = 15,
        cancelledImports = 5,
        runningImports = 2,
        manualImports = 60,
        automaticImports = 40,
        successRate = 0.8,
      )

    every { importHistoryService.getImportStatistics() } returns stats

    val result = controller.getStatistics()

    assertThat(result.totalImports).isEqualTo(100)
    assertThat(result.completedImports).isEqualTo(80)
    assertThat(result.failedImports).isEqualTo(15)
    assertThat(result.cancelledImports).isEqualTo(5)
    assertThat(result.runningImports).isEqualTo(2)
    assertThat(result.manualImports).isEqualTo(60)
    assertThat(result.automaticImports).isEqualTo(40)
    assertThat(result.successRate).isEqualTo(0.8)
  }

  // ========== Helper Methods ==========

  private fun createFeedImport(status: ImportStatus): FeedImport =
    FeedImport(
      id = ImportId(importId),
      feedId = feedId,
      administrator = null,
      triggerType = ImportTriggerType.MANUAL,
      status = status,
      versionSha1 = "abc123",
      startedAt = createdAt,
      completedAt = if (status == ImportStatus.COMPLETED) createdAt.plusSeconds(300) else null,
      fileSizeBytes = 1024,
      errorMessage = if (status == ImportStatus.FAILED) "Test error" else null,
      createdAt = createdAt,
      updatedAt = createdAt,
    )

  private fun createFeed(): FeedEntity =
    FeedEntity(
      feedId = feedId,
      regions = mutableSetOf(),
      name = "Test Feed",
      downloadUrl = "https://example.com/gtfs.zip",
      specType = FeedSpecType.GTFS,
      status = FeedStatus.ACTIVE,
      createdAt = createdAt,
      updatedAt = createdAt,
    )
}
