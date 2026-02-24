package com.mobilispect.backend.region.service

import com.mobilispect.backend.feed.model.ImportTriggerType
import com.mobilispect.backend.region.RegionId
import com.mobilispect.backend.region.data.repository.RegionImportRepository
import com.mobilispect.backend.region.domain.RegionImport
import com.mobilispect.backend.region.domain.RegionImportId
import com.mobilispect.backend.region.domain.RegionImportStatus
import io.mockk.every
import io.mockk.mockk
import java.util.Optional
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class RegionImportServiceTest {
  private lateinit var regionImportRepository: RegionImportRepository
  private lateinit var service: RegionImportService

  @BeforeEach
  fun setUp() {
    regionImportRepository = mockk()
    service = RegionImportService(regionImportRepository = regionImportRepository)
  }

  @Test
  fun `getRegionImport returns import when found`() {
    val regionImportId = RegionImportId.random()
    val regionImport =
      RegionImport(
        id = regionImportId,
        regionOnestopId = "r-test",
        triggerType = ImportTriggerType.MANUAL,
        status = RegionImportStatus.RUNNING,
        totalFeeds = 2,
      )

    every { regionImportRepository.findByImportId(regionImportId) } returns
      Optional.of(regionImport)

    val result = service.getRegionImport(regionImportId)

    assertThat(result).isEqualTo(regionImport)
  }

  @Test
  fun `getRegionImport returns null when not found`() {
    val regionImportId = RegionImportId.random()

    every { regionImportRepository.findByImportId(regionImportId) } returns Optional.empty()

    val result = service.getRegionImport(regionImportId)

    assertThat(result).isNull()
  }

  @Test
  fun `getActiveImportForRegion returns active import when one exists`() {
    val regionId = RegionId("r-active")
    val activeStatuses = listOf(RegionImportStatus.PENDING, RegionImportStatus.RUNNING)
    val regionImport =
      RegionImport(
        id = RegionImportId.random(),
        regionOnestopId = regionId.value,
        triggerType = ImportTriggerType.MANUAL,
        status = RegionImportStatus.RUNNING,
        totalFeeds = 3,
      )

    every {
      regionImportRepository.findActiveByRegionOnestopId(regionId.value, activeStatuses)
    } returns Optional.of(regionImport)

    val result = service.getActiveImportForRegion(regionId)

    assertThat(result).isEqualTo(regionImport)
  }

  @Test
  fun `getActiveImportForRegion returns null when no active import`() {
    val regionId = RegionId("r-quiet")
    val activeStatuses = listOf(RegionImportStatus.PENDING, RegionImportStatus.RUNNING)

    every {
      regionImportRepository.findActiveByRegionOnestopId(regionId.value, activeStatuses)
    } returns Optional.empty()

    val result = service.getActiveImportForRegion(regionId)

    assertThat(result).isNull()
  }

  @Test
  fun `getActiveRegionImports returns all pending and running imports`() {
    val activeStatuses = listOf(RegionImportStatus.PENDING, RegionImportStatus.RUNNING)
    val imports =
      listOf(
        RegionImport(
          id = RegionImportId.random(),
          regionOnestopId = "r-1",
          triggerType = ImportTriggerType.MANUAL,
          status = RegionImportStatus.PENDING,
          totalFeeds = 1,
        ),
        RegionImport(
          id = RegionImportId.random(),
          regionOnestopId = "r-2",
          triggerType = ImportTriggerType.MANUAL,
          status = RegionImportStatus.RUNNING,
          totalFeeds = 2,
        ),
      )

    every { regionImportRepository.findAllByStatusInOrderByCreatedAtAsc(activeStatuses) } returns
      imports

    val result = service.getActiveRegionImports()

    assertThat(result).hasSize(2)
    assertThat(result).containsExactlyElementsOf(imports)
  }
}
