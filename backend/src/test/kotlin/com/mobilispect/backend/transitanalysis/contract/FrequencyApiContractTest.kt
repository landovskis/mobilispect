package com.mobilispect.backend.transitanalysis.contract

import com.mobilispect.backend.route.api.CommonSectionController
import com.mobilispect.backend.route.api.FrequencyController
import com.mobilispect.backend.route.api.dto.CombinedFrequencyDTO
import com.mobilispect.backend.route.api.dto.CommonSectionDTO
import com.mobilispect.backend.route.api.dto.FrequencyDTO
import com.mobilispect.backend.route.api.dto.RouteDTO
import com.mobilispect.backend.route.api.dto.RouteVariantDTO
import com.mobilispect.backend.route.application.CommonSectionService
import com.mobilispect.backend.route.application.FrequencyQueryService
import com.mobilispect.backend.route.domain.model.RouteType
import com.mobilispect.backend.route.domain.model.TimePeriod
import java.util.UUID
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.Mockito.`when`
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.eq
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.setup.MockMvcBuilders

class FrequencyApiContractTest {

  private val frequencyQueryService: FrequencyQueryService =
    org.mockito.Mockito.mock(FrequencyQueryService::class.java)
  private val commonSectionService: CommonSectionService =
    org.mockito.Mockito.mock(CommonSectionService::class.java)

  private val mockMvc: MockMvc =
    MockMvcBuilders.standaloneSetup(
        FrequencyController(frequencyQueryService),
        CommonSectionController(commonSectionService),
      )
      .build()

  @Test
  fun `GET route matches contract`() {
    `when`(frequencyQueryService.getRoute(any()))
      .thenReturn(
        RouteDTO(
          id = "r-1",
          agencyId = "o-1",
          shortName = "1",
          longName = "Route 1",
          routeType = RouteType.BUS,
          active = true,
        )
      )

    val response =
      mockMvc.get("/api/v1/routes/r-1") { accept(MediaType.APPLICATION_JSON) }.andReturn().response

    assertThat(response.status).isEqualTo(200)
    assertThat(response.contentAsString).contains("Route 1")
  }

  @Test
  fun `GET variants matches contract`() {
    `when`(frequencyQueryService.getVariantsByRoute(any()))
      .thenReturn(
        listOf(
          RouteVariantDTO(
            id = "v1",
            routeId = "r-1",
            directionId = 0,
            headsign = "Downtown",
            stopCount = 5,
            stopPattern = "s1|s2|s3|s4|s5",
            stopNames = listOf("Stop 1", "Stop 2", "Stop 3", "Stop 4", "Stop 5"),
            firstStopId = "s1",
            lastStopId = "s5",
          )
        )
      )

    val response =
      mockMvc
        .get("/api/v1/routes/r-1/variants") { accept(MediaType.APPLICATION_JSON) }
        .andReturn()
        .response

    assertThat(response.status).isEqualTo(200)
    assertThat(response.contentAsString).contains("Downtown")
  }

  @Test
  fun `GET variant frequencies matches contract`() {
    `when`(frequencyQueryService.getFrequenciesForVariant(any(), anyOrNull()))
      .thenReturn(
        listOf(
          FrequencyDTO(
            id = UUID.randomUUID().toString(),
            variantId = "a".repeat(64),
            serviceDate = "2025-01-01",
            timePeriod = TimePeriod.WEEKDAY_AM_PEAK,
            averageHeadwayMinutes = 10.0,
            minHeadwayMinutes = 8.0,
            maxHeadwayMinutes = 12.0,
            tripCount = 5,
            isIrregular = false,
          )
        )
      )

    val response =
      mockMvc
        .get("/api/v1/routes/variants/${"a".repeat(64)}/frequencies") {
          accept(MediaType.APPLICATION_JSON)
        }
        .andReturn()
        .response

    assertThat(response.status).isEqualTo(200)
    assertThat(response.contentAsString).contains("WEEKDAY_AM_PEAK")
  }

  @Test
  fun `GET common sections matches contract`() {
    `when`(commonSectionService.getCommonSectionsForRoute(any()))
      .thenReturn(
        listOf(
          CommonSectionDTO(
            id = UUID.randomUUID().toString(),
            stopPattern = "s1|s2|s3",
            stopCount = 3,
            firstStopId = "s1",
            lastStopId = "s3",
            variants = listOf("v1", "v2"),
          )
        )
      )

    val response =
      mockMvc
        .get("/api/v1/common-sections/routes/r-1") { accept(MediaType.APPLICATION_JSON) }
        .andReturn()
        .response

    assertThat(response.status).isEqualTo(200)
    assertThat(response.contentAsString).contains("s1|s2|s3")
  }

  @Test
  fun `GET common section frequency matches contract`() {
    val id = UUID.randomUUID()
    `when`(commonSectionService.getCombinedFrequency(eq(id), eq(TimePeriod.WEEKDAY_AM_PEAK)))
      .thenReturn(
        CombinedFrequencyDTO(
          commonSectionId = id.toString(),
          timePeriod = "WEEKDAY_AM_PEAK",
          averageHeadwayMinutes = 8.0,
          tripCount = 10,
          isIrregular = false,
        )
      )

    val response =
      mockMvc
        .get("/api/v1/common-sections/$id/frequency?timePeriod=WEEKDAY_AM_PEAK") {
          accept(MediaType.APPLICATION_JSON)
        }
        .andReturn()
        .response

    assertThat(response.status).isEqualTo(200)
    assertThat(response.contentAsString).contains("averageHeadwayMinutes")
  }
}
