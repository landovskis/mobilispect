package com.mobilispect.backend.agency.contract

import com.mobilispect.backend.agency.api.AgencyController
import com.mobilispect.backend.agency.api.dto.AgencyDTO
import com.mobilispect.backend.agency.api.dto.AgencySummaryDTO
import com.mobilispect.backend.agency.application.AgencyQueryService
import com.mobilispect.backend.route.domain.model.RouteType
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.Mockito.`when`
import org.mockito.kotlin.any
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.PageRequest
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.setup.MockMvcBuilders

/**
 * Contract tests for Agency API endpoints (User Story 1).
 *
 * Task T109-T110: Verify API responses match expected contract structure for regional transit
 * frequency overview functionality.
 *
 * Tests ensure:
 * - Correct HTTP status codes
 * - Valid JSON response structure
 * - Required fields are present
 * - Data types match DTOs
 */
class AgencyApiContractTest {

  private val agencyQueryService: AgencyQueryService =
    org.mockito.Mockito.mock(AgencyQueryService::class.java)

  private val mockMvc: MockMvc =
    MockMvcBuilders.standaloneSetup(AgencyController(agencyQueryService))
      .setCustomArgumentResolvers(
        org.springframework.data.web.PageableHandlerMethodArgumentResolver()
      )
      .build()

  @Test
  fun `GET agencies matches contract`() {
    val agencyDTO =
      AgencyDTO(
        id = "o-123",
        name = "Test Transit Agency",
        feedOnestopId = "f-abc",
        regionIds = setOf("r-1", "r-2"),
        routeCount = 15,
        activeRouteCount = 12,
        routesByType = mapOf(RouteType.BUS to 10, RouteType.RAIL to 5),
      )

    `when`(agencyQueryService.getAgencies(any()))
      .thenReturn(PageImpl(listOf(agencyDTO), PageRequest.of(0, 20), 1))

    val response =
      mockMvc
        .get("/api/agencies") {
          accept(MediaType.APPLICATION_JSON)
          param("page", "0")
          param("size", "20")
        }
        .andReturn()
        .response

    assertThat(response.status).isEqualTo(200)
    assertThat(response.contentType).contains(MediaType.APPLICATION_JSON_VALUE)
    assertThat(response.contentAsString).contains("Test Transit Agency")
    assertThat(response.contentAsString).contains("o-123")
    assertThat(response.contentAsString).contains("routeCount")
    assertThat(response.contentAsString).contains("activeRouteCount")
    assertThat(response.contentAsString).contains("routesByType")
  }

  @Test
  fun `GET agencies by region matches contract`() {
    val agencyDTO =
      AgencyDTO(
        id = "o-456",
        name = "Regional Transit Authority",
        feedOnestopId = "f-xyz",
        regionIds = setOf("r-bay-area"),
        routeCount = 25,
        activeRouteCount = 20,
        routesByType = mapOf(RouteType.BUS to 15, RouteType.RAIL to 8, RouteType.FERRY to 2),
      )

    `when`(agencyQueryService.getAgenciesByRegion(any(), any()))
      .thenReturn(PageImpl(listOf(agencyDTO), PageRequest.of(0, 20), 1))

    val response =
      mockMvc
        .get("/api/regions/r-bay-area/agencies") {
          accept(MediaType.APPLICATION_JSON)
          param("page", "0")
          param("size", "20")
        }
        .andReturn()
        .response

    assertThat(response.status).isEqualTo(200)
    assertThat(response.contentType).contains(MediaType.APPLICATION_JSON_VALUE)
    assertThat(response.contentAsString).contains("Regional Transit Authority")
    assertThat(response.contentAsString).contains("o-456")
    assertThat(response.contentAsString).contains("r-bay-area")
    assertThat(response.contentAsString).contains("BUS")
    assertThat(response.contentAsString).contains("RAIL")
    assertThat(response.contentAsString).contains("FERRY")
  }

  @Test
  fun `GET agency summary matches contract`() {
    val agencySummary =
      AgencySummaryDTO(
        id = "o-789",
        name = "Metro Transit Services",
        routeCount = 42,
        averageHeadwayMinutes = 12.5,
        minHeadwayMinutes = 5.0,
        maxHeadwayMinutes = 30.0,
      )

    `when`(agencyQueryService.getAgencySummary(any())).thenReturn(agencySummary)

    val response =
      mockMvc.get("/api/agencies/o-789") { accept(MediaType.APPLICATION_JSON) }.andReturn().response

    assertThat(response.status).isEqualTo(200)
    assertThat(response.contentType).contains(MediaType.APPLICATION_JSON_VALUE)
    assertThat(response.contentAsString).contains("Metro Transit Services")
    assertThat(response.contentAsString).contains("o-789")
    assertThat(response.contentAsString).contains("routeCount")
    assertThat(response.contentAsString).contains("42")
  }

  @Test
  fun `GET agency summary returns 200 with null for non-existent agency`() {
    `when`(agencyQueryService.getAgencySummary(any())).thenReturn(null)

    val response =
      mockMvc
        .get("/api/agencies/o-nonexistent") { accept(MediaType.APPLICATION_JSON) }
        .andReturn()
        .response

    assertThat(response.status).isEqualTo(200)
    assertThat(response.contentAsString).isEmpty()
  }

  @Test
  fun `GET agencies returns empty page when no agencies exist`() {
    `when`(agencyQueryService.getAgencies(any()))
      .thenReturn(PageImpl(emptyList(), PageRequest.of(0, 20), 0))

    val response =
      mockMvc.get("/api/agencies") { accept(MediaType.APPLICATION_JSON) }.andReturn().response

    assertThat(response.status).isEqualTo(200)
    assertThat(response.contentType).contains(MediaType.APPLICATION_JSON_VALUE)
    assertThat(response.contentAsString).contains("\"content\":[]")
  }

  @Test
  fun `GET agencies by region supports pagination`() {
    val agency1 =
      AgencyDTO(
        id = "o-1",
        name = "Agency 1",
        feedOnestopId = "f-1",
        regionIds = setOf("r-test"),
        routeCount = 10,
        activeRouteCount = 8,
        routesByType = mapOf(RouteType.BUS to 10),
      )
    val agency2 =
      AgencyDTO(
        id = "o-2",
        name = "Agency 2",
        feedOnestopId = "f-2",
        regionIds = setOf("r-test"),
        routeCount = 5,
        activeRouteCount = 4,
        routesByType = mapOf(RouteType.RAIL to 5),
      )

    `when`(agencyQueryService.getAgenciesByRegion(any(), any()))
      .thenReturn(PageImpl(listOf(agency1, agency2), PageRequest.of(0, 2), 2))

    val response =
      mockMvc
        .get("/api/regions/r-test/agencies") {
          accept(MediaType.APPLICATION_JSON)
          param("page", "0")
          param("size", "2")
        }
        .andReturn()
        .response

    assertThat(response.status).isEqualTo(200)
    assertThat(response.contentAsString).contains("Agency 1")
    assertThat(response.contentAsString).contains("Agency 2")
    assertThat(response.contentAsString).contains("totalElements")
  }
}
