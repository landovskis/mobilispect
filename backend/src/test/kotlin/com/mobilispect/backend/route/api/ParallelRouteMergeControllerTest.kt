package com.mobilispect.backend.route.api

import com.mobilispect.backend.feed.domain.model.ids.FeedId
import com.mobilispect.backend.route.RouteId
import com.mobilispect.backend.route.application.ParallelRouteMergeService
import com.mobilispect.backend.route.domain.model.ParallelRouteGroup
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.setup.MockMvcBuilders

class ParallelRouteMergeControllerTest {

  private lateinit var mergeService: ParallelRouteMergeService
  private lateinit var controller: ParallelRouteMergeController
  private lateinit var mockMvc: MockMvc

  @BeforeEach
  fun setUp() {
    mergeService = mockk()
    controller = ParallelRouteMergeController(mergeService)
    mockMvc = MockMvcBuilders.standaloneSetup(controller).build()
  }

  @Test
  fun `GET parallel-routes returns 200 with groups`() {
    val group =
      ParallelRouteGroup(
        routeIds = setOf(RouteId("r-1"), RouteId("r-2")),
        variantIds = setOf("v-001", "v-002"),
        mergedStopSequence = listOf("stop-a", "stop-b", "stop-c"),
        averageDistanceMeters = 89.5,
      )

    every {
      mergeService.findParallelRouteGroups(
        feedId = FeedId("f-abc-test"),
        distanceThresholdMeters = 200.0,
        minimumFrequencyMinutes = null,
      )
    } returns listOf(group)

    val result =
      mockMvc
        .get("/api/v1/routes/parallel") {
          param("feedId", "f-abc-test")
          param("distanceThresholdMeters", "200.0")
          accept(MediaType.APPLICATION_JSON)
        }
        .andReturn()

    assertThat(result.response.status).isEqualTo(200)
    val body = result.response.contentAsString
    assertThat(body).contains("r-1")
    assertThat(body).contains("r-2")
    assertThat(body).contains("89.5")
    assertThat(body).contains("stop-a")
  }

  @Test
  fun `GET parallel-routes passes minimumFrequencyMinutes to service when provided`() {
    every {
      mergeService.findParallelRouteGroups(
        feedId = FeedId("f-abc-test"),
        distanceThresholdMeters = 200.0,
        minimumFrequencyMinutes = 30.0,
      )
    } returns emptyList()

    val result =
      mockMvc
        .get("/api/v1/routes/parallel") {
          param("feedId", "f-abc-test")
          param("distanceThresholdMeters", "200.0")
          param("minimumFrequencyMinutes", "30.0")
          accept(MediaType.APPLICATION_JSON)
        }
        .andReturn()

    assertThat(result.response.status).isEqualTo(200)
    verify(exactly = 1) {
      mergeService.findParallelRouteGroups(
        feedId = FeedId("f-abc-test"),
        distanceThresholdMeters = 200.0,
        minimumFrequencyMinutes = 30.0,
      )
    }
  }

  @Test
  fun `GET parallel-routes returns empty array when no groups found`() {
    every { mergeService.findParallelRouteGroups(any(), any(), any()) } returns emptyList()

    val result =
      mockMvc
        .get("/api/v1/routes/parallel") {
          param("feedId", "f-abc-test")
          param("distanceThresholdMeters", "200.0")
          accept(MediaType.APPLICATION_JSON)
        }
        .andReturn()

    assertThat(result.response.status).isEqualTo(200)
    assertThat(result.response.contentAsString).isEqualTo("[]")
  }

  @Test
  fun `GET parallel-routes returns 400 when feedId is missing`() {
    val result =
      mockMvc
        .get("/api/v1/routes/parallel") {
          param("distanceThresholdMeters", "200.0")
          accept(MediaType.APPLICATION_JSON)
        }
        .andReturn()

    assertThat(result.response.status).isEqualTo(400)
  }

  @Test
  fun `GET parallel-routes returns 400 when distanceThresholdMeters is missing`() {
    val result =
      mockMvc
        .get("/api/v1/routes/parallel") {
          param("feedId", "f-abc-test")
          accept(MediaType.APPLICATION_JSON)
        }
        .andReturn()

    assertThat(result.response.status).isEqualTo(400)
  }
}
