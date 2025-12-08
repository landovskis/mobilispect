package com.mobilispect.backend.transitanalysis.api

import com.mobilispect.backend.transitanalysis.api.dto.AgencyDTO
import com.mobilispect.backend.transitanalysis.api.dto.AgencySummaryDTO
import com.mobilispect.backend.transitanalysis.application.AgencyQueryService
import io.mockk.every
import io.mockk.mockk
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.PageRequest

class FrequencyAnalysisControllerTest {

    private lateinit var agencyQueryService: AgencyQueryService
    private lateinit var controller: FrequencyAnalysisController

    @BeforeEach
    fun setUp() {
        agencyQueryService = mockk()
        controller = FrequencyAnalysisController(agencyQueryService)
    }

    @Test
    fun `listAgencies returns paged agencies`() {
        val dto = AgencyDTO(
            id = "o-123",
            name = "Agency",
            feedOnestopId = "f-abc",
            regionIds = emptySet(),
            routeCount = 2,
            activeRouteCount = 1,
            routesByType = emptyMap()
        )
        every { agencyQueryService.getAgencies(any()) } returns PageImpl(listOf(dto))

        val result = controller.listAgencies(PageRequest.of(0, 20))

        assertThat(result.content).hasSize(1)
        assertThat(result.content.first().id).isEqualTo("o-123")
    }

    @Test
    fun `getAgency returns summary`() {
        val summary = AgencySummaryDTO(
            id = "o-123",
            name = "Agency",
            routeCount = 2,
            averageHeadwayMinutes = null,
            minHeadwayMinutes = null,
            maxHeadwayMinutes = null
        )
        every {
            agencyQueryService.getAgencySummary(
                com.mobilispect.backend.transitanalysis.domain.model.ids.AgencyId("o-123")
            )
        } returns summary

        val result = controller.getAgency("o-123")

        assertThat(result).isNotNull
        assertThat(result?.id).isEqualTo("o-123")
    }

    @Test
    fun `listAgenciesByRegion returns paged agencies`() {
        val dto = AgencyDTO(
            id = "o-123",
            name = "Agency",
            feedOnestopId = "f-abc",
            regionIds = setOf("r-1"),
            routeCount = 2,
            activeRouteCount = 1,
            routesByType = emptyMap()
        )
        every {
            agencyQueryService.getAgenciesByRegion(
                com.mobilispect.backend.feed.model.ids.RegionId("r-1"),
                any()
            )
        } returns PageImpl(listOf(dto))

        val result = controller.listAgenciesByRegion("r-1", PageRequest.of(0, 20))

        assertThat(result.content).hasSize(1)
        assertThat(result.content.first().regionIds).contains("r-1")
    }
}
