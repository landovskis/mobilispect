package com.mobilispect.backend.transitanalysis.api

import com.mobilispect.backend.route.api.FrequencyController
import com.mobilispect.backend.route.api.dto.RouteDTO
import com.mobilispect.backend.route.application.FrequencyQueryService
import com.mobilispect.backend.route.domain.model.RouteType
import com.mobilispect.backend.route.domain.model.ids.RouteId
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.setup.MockMvcBuilders

class FrequencyControllerTest {
  private val service: FrequencyQueryService = mock(FrequencyQueryService::class.java)
  private val controller = FrequencyController(service)
  private val mockMvc: MockMvc = MockMvcBuilders.standaloneSetup(controller).build()

  @Test
  fun `getRoute returns 200`() {
    `when`(service.getRoute(RouteId("r-1")))
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

    val result =
      mockMvc.get("/api/v1/routes/r-1") { accept(MediaType.APPLICATION_JSON) }.andReturn()

    assertThat(result.response.status).isEqualTo(200)
  }
}
