package com.mobilispect.backend.transitanalysis.api

import com.mobilispect.backend.transitanalysis.api.dto.AgencyDTO
import com.mobilispect.backend.transitanalysis.api.dto.AgencySummaryDTO
import com.mobilispect.backend.transitanalysis.application.AgencyQueryService
import io.mockk.any
import io.mockk.every
import io.mockk.mockk
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.PageRequest
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.setup.MockMvcBuilders

class FrequencyAnalysisControllerTest {

    private val agencyQueryService: AgencyQueryService = mockk()
    private val controller = FrequencyAnalysisController(agencyQueryService)
    private val mockMvc: MockMvc = MockMvcBuilders.standaloneSetup(controller).build()

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
        every { agencyQueryService.getAgencies(PageRequest.of(0, 20)) } returns PageImpl(listOf(dto))

        val mvcResult = mockMvc.get("/api/v1/frequency/agencies?page=0&size=20") {
            accept(MediaType.APPLICATION_JSON)
        }.andReturn()

        assertThat(mvcResult.response.status).isEqualTo(200)
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

        val mvcResult = mockMvc.get("/api/v1/frequency/agencies/o-123") {
            accept(MediaType.APPLICATION_JSON)
        }.andReturn()

        assertThat(mvcResult.response.status).isEqualTo(200)
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

        val mvcResult = mockMvc.get("/api/v1/frequency/regions/r-1/agencies?page=0&size=20") {
            accept(MediaType.APPLICATION_JSON)
        }.andReturn()

        assertThat(mvcResult.response.status).isEqualTo(200)
    }
}
