package com.mobilispect.backend.feed.controller

import com.mobilispect.backend.feed.FeedImportSummaryDTO
import com.mobilispect.backend.feed.domain.ImportStatus
import com.mobilispect.backend.feed.domain.TriggerType
import com.mobilispect.backend.feed.service.FeedImportQueryService
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import java.time.Instant
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class FeedImportControllerTest {
  private lateinit var feedImportQueryService: FeedImportQueryService
  private lateinit var controller: FeedImportController

  private val fixedInstant = Instant.parse("2026-01-07T12:00:00Z")

  @BeforeEach
  fun setUp() {
    feedImportQueryService = mockk()
    controller = FeedImportController(feedImportQueryService = feedImportQueryService)
  }

  @Test
  fun `GET active imports returns running imports with feed and region names`() {
    // Given: Active imports exist
    val activeImports =
      listOf(
        createFeedImportSummary(
          id = "import-1",
          feedId = "f-bart",
          feedName = "BART",
          regionId = "r-san-francisco-bay-area",
          regionName = "San Francisco Bay Area",
          status = ImportStatus.RUNNING,
        ),
        createFeedImportSummary(
          id = "import-2",
          feedId = "f-muni",
          feedName = "MUNI",
          regionId = "r-san-francisco-bay-area",
          regionName = "San Francisco Bay Area",
          status = ImportStatus.RUNNING,
        ),
        createFeedImportSummary(
          id = "import-3",
          feedId = "f-caltrain",
          feedName = "Caltrain",
          regionId = "r-san-francisco-bay-area",
          regionName = "San Francisco Bay Area",
          status = ImportStatus.PENDING,
        ),
      )

    every { feedImportQueryService.getActiveImports() } returns activeImports

    // When: GET /api/feeds/imports/active
    val response = controller.getActiveImports()

    // Then: Returns ActiveImportsResponse with FeedImportSummaryDTOs
    assertThat(response.imports).hasSize(3)
    assertThat(response.total).isEqualTo(3)
    assertThat(response.imports.map { it.feedOnestopId })
      .containsExactlyInAnyOrder("f-bart", "f-muni", "f-caltrain")
    assertThat(response.imports.map { it.feedName })
      .containsExactlyInAnyOrder("BART", "MUNI", "Caltrain")
    assertThat(response.imports).allMatch { it.regionName == "San Francisco Bay Area" }
    assertThat(response.imports).allMatch { it.regionOnestopId == "r-san-francisco-bay-area" }
    verify { feedImportQueryService.getActiveImports() }
  }

  @Test
  fun `GET active imports returns empty list when no active imports`() {
    // Given: No active imports
    every { feedImportQueryService.getActiveImports() } returns emptyList()

    // When: GET /api/feeds/imports/active
    val response = controller.getActiveImports()

    // Then: Returns empty imports list
    assertThat(response.imports).isEmpty()
    assertThat(response.total).isEqualTo(0)
    verify { feedImportQueryService.getActiveImports() }
  }

  @Test
  fun `GET active imports filters by PENDING and RUNNING status only`() {
    // Given: Only PENDING and RUNNING imports are returned
    val activeImports =
      listOf(
        createFeedImportSummary(
          id = "import-1",
          feedId = "f-bart",
          feedName = "BART",
          regionId = "r-san-francisco-bay-area",
          regionName = "San Francisco Bay Area",
          status = ImportStatus.RUNNING,
        ),
        createFeedImportSummary(
          id = "import-2",
          feedId = "f-muni",
          feedName = "MUNI",
          regionId = "r-san-francisco-bay-area",
          regionName = "San Francisco Bay Area",
          status = ImportStatus.PENDING,
        ),
      )

    every { feedImportQueryService.getActiveImports() } returns activeImports

    // When: GET /api/feeds/imports/active
    val response = controller.getActiveImports()

    // Then: Only active statuses are present
    assertThat(response.imports).hasSize(2)
    assertThat(response.imports).allMatch {
      it.status == ImportStatus.RUNNING || it.status == ImportStatus.PENDING
    }
    assertThat(response.imports).noneMatch { it.status == ImportStatus.COMPLETED }
    assertThat(response.imports).noneMatch { it.status == ImportStatus.FAILED }
    assertThat(response.imports).noneMatch { it.status == ImportStatus.CANCELLED }
  }

  @Test
  fun `GET active imports includes imports from multiple regions`() {
    // Given: Active imports from different regions
    val activeImports =
      listOf(
        createFeedImportSummary(
          id = "import-1",
          feedId = "f-bart",
          feedName = "BART",
          regionId = "r-san-francisco-bay-area",
          regionName = "San Francisco Bay Area",
          status = ImportStatus.RUNNING,
        ),
        createFeedImportSummary(
          id = "import-2",
          feedId = "f-mta",
          feedName = "MTA New York",
          regionId = "r-new-york-city",
          regionName = "New York City",
          status = ImportStatus.RUNNING,
        ),
      )

    every { feedImportQueryService.getActiveImports() } returns activeImports

    // When: GET /api/feeds/imports/active
    val response = controller.getActiveImports()

    // Then: Both regions are represented
    assertThat(response.imports).hasSize(2)
    assertThat(response.imports.map { it.regionName })
      .containsExactlyInAnyOrder("San Francisco Bay Area", "New York City")
    assertThat(response.imports.map { it.regionOnestopId })
      .containsExactlyInAnyOrder("r-san-francisco-bay-area", "r-new-york-city")
  }

  @Test
  fun `GET active imports handles feed with no region gracefully`() {
    // Given: Active import with feed that has no region
    val activeImports =
      listOf(
        createFeedImportSummary(
          id = "import-1",
          feedId = "f-orphan",
          feedName = "Orphan Feed",
          regionId = null,
          regionName = null,
          status = ImportStatus.RUNNING,
        )
      )

    every { feedImportQueryService.getActiveImports() } returns activeImports

    // When: GET /api/feeds/imports/active
    val response = controller.getActiveImports()

    // Then: Feed import is included with null region data
    assertThat(response.imports).hasSize(1)
    assertThat(response.imports.first().regionName).isNull()
    assertThat(response.imports.first().regionOnestopId).isNull()
  }

  private fun createFeedImportSummary(
    id: String,
    feedId: String,
    feedName: String,
    regionId: String?,
    regionName: String?,
    status: ImportStatus,
  ): FeedImportSummaryDTO {
    return FeedImportSummaryDTO(
      id = id,
      feedOnestopId = feedId,
      feedName = feedName,
      regionOnestopId = regionId,
      regionName = regionName,
      status = status,
      triggerType = TriggerType.MANUAL,
      startedAt = fixedInstant,
      completedAt = null,
      progress = null,
      currentStep = null,
    )
  }
}
