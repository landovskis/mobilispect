package com.mobilispect.backend.transitanalysis.api

import com.mobilispect.backend.region.RegionId
import com.mobilispect.backend.route.api.CorridorController
import com.mobilispect.backend.route.api.dto.CorridorDTO
import com.mobilispect.backend.route.api.dto.CorridorRouteDTO
import com.mobilispect.backend.route.application.CorridorQueryService
import java.util.UUID
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.setup.MockMvcBuilders

class CorridorControllerTest {
  private val service: CorridorQueryService = mock(CorridorQueryService::class.java)
  private val controller = CorridorController(service)
  private val mockMvc: MockMvc = MockMvcBuilders.standaloneSetup(controller).build()

  @Test
  fun `getCorridorsForRegion returns 200 with corridors`() {
    val regionId = "r-abc"
    `when`(service.getCorridorsForRegion(RegionId(regionId)))
      .thenReturn(
        listOf(
          CorridorDTO(
            id = UUID.randomUUID().toString(),
            stopPattern = "s1|s2|s3",
            stopCount = 3,
            firstStopId = "s1",
            lastStopId = "s3",
            routes =
              listOf(
                CorridorRouteDTO(routeId = "r-1", shortName = "10", longName = "Route 10"),
                CorridorRouteDTO(routeId = "r-2", shortName = "20", longName = "Route 20"),
              ),
          )
        )
      )

    val result =
      mockMvc
        .get("/api/v1/regions/$regionId/corridors") { accept(MediaType.APPLICATION_JSON) }
        .andReturn()

    assertThat(result.response.status).isEqualTo(200)
  }

  @Test
  fun `getCorridorsForRegion returns 200 with empty list when no corridors`() {
    val regionId = "r-abc"
    `when`(service.getCorridorsForRegion(RegionId(regionId))).thenReturn(emptyList())

    val result =
      mockMvc
        .get("/api/v1/regions/$regionId/corridors") { accept(MediaType.APPLICATION_JSON) }
        .andReturn()

    assertThat(result.response.status).isEqualTo(200)
  }
}
