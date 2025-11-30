package com.mobilispect.backend.transitanalysis.api

import com.mobilispect.backend.transitanalysis.api.dto.CommonSectionDTO
import com.mobilispect.backend.transitanalysis.api.dto.CombinedFrequencyDTO
import com.mobilispect.backend.transitanalysis.api.dto.RouteContributionDTO
import com.mobilispect.backend.transitanalysis.application.CommonSectionService
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.Mockito.`when`
import org.mockito.Mockito.mock
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import java.util.UUID

class CommonSectionControllerTest {
    private val service: CommonSectionService = mock(CommonSectionService::class.java)
    private val controller = CommonSectionController(service)
    private val mockMvc: MockMvc = MockMvcBuilders.standaloneSetup(controller).build()

    @Test
    fun `getCommonSectionsForRoute returns 200`() {
        `when`(service.getCommonSectionsForRoute(com.mobilispect.backend.transitanalysis.domain.model.ids.RouteId("r-1"))).thenReturn(
            listOf(
                CommonSectionDTO(
                    id = UUID.randomUUID().toString(),
                    stopPattern = "s1|s2|s3",
                    stopCount = 3,
                    firstStopId = "s1",
                    lastStopId = "s3",
                    variants = listOf("v1")
                )
            )
        )
        val result = mockMvc.get("/api/v1/common-sections/routes/r-1") {
            accept(MediaType.APPLICATION_JSON)
        }.andReturn()
        assertThat(result.response.status).isEqualTo(200)
    }

    @Test
    fun `getCombinedFrequency returns 200`() {
        val id = UUID.randomUUID()
        `when`(service.getCombinedFrequency(id, com.mobilispect.backend.transitanalysis.domain.model.TimePeriod.WEEKDAY_AM_PEAK)).thenReturn(
            CombinedFrequencyDTO(
                commonSectionId = id.toString(),
                timePeriod = "WEEKDAY_AM_PEAK",
                averageHeadwayMinutes = 10.0,
                tripCount = 4,
                isIrregular = false,
                contributions = listOf(
                    RouteContributionDTO(routeId = "r-1", averageHeadwayMinutes = 10.0, tripCount = 4, isIrregular = false)
                )
            )
        )
        val result = mockMvc.get("/api/v1/common-sections/$id/frequency?timePeriod=WEEKDAY_AM_PEAK") {
            accept(MediaType.APPLICATION_JSON)
        }.andReturn()
        assertThat(result.response.status).isEqualTo(200)
    }
}
