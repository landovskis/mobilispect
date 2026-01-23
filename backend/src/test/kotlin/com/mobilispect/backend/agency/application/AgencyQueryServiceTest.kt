package com.mobilispect.backend.agency.application

import com.mobilispect.backend.agency.AgencyId
import com.mobilispect.backend.agency.domain.model.Agency
import com.mobilispect.backend.agency.domain.repository.AgencyRepository
import com.mobilispect.backend.feed.FeedApi
import com.mobilispect.backend.feed.api.FeedDTO
import com.mobilispect.backend.feed.domain.model.Feed
import com.mobilispect.backend.feed.domain.model.ids.FeedId
import com.mobilispect.backend.feed.model.FeedSpecType
import com.mobilispect.backend.feed.model.FeedStatus
import com.mobilispect.backend.region.RegionId
import com.mobilispect.backend.route.RouteId
import com.mobilispect.backend.route.domain.model.Route
import com.mobilispect.backend.route.domain.model.RouteType
import com.mobilispect.backend.route.domain.repository.RouteRepository
import java.time.Instant
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.any
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Pageable

@ExtendWith(MockitoExtension::class)
class AgencyQueryServiceTest {
  private val agencyRepository: AgencyRepository = mock(AgencyRepository::class.java)
  private val routeRepository: RouteRepository = mock(RouteRepository::class.java)
  private val feedQueryApi: FeedApi = mock(FeedApi::class.java)
  private val service = AgencyQueryService(agencyRepository, routeRepository, feedQueryApi)

  @Test
  fun `getAgencies maps agency and routes into DTO`() {
    val now = Instant.now()
    val feedDTO =
      FeedDTO(
        feedId = FeedId("f-abc"),
        name = "Test Feed",
        specType = FeedSpecType.GTFS,
        downloadUrl = "https://example.com/gtfs.zip",
        currentVersionSha1 = null,
        status = FeedStatus.ACTIVE,
        regionIds = setOf(RegionId("r-1")),
        lastCheckedAt = null,
        lastUpdatedAt = null,
        lastDiscoveredAt = null,
        createdAt = now,
        updatedAt = now,
      )
    val agency =
      Agency(
        agencyId = AgencyId("o-123"),
        feedId = FeedId("f-abc"),
        gtfsAgencyId = "123",
        name = "Test Agency",
        createdAt = Instant.now(),
        updatedAt = Instant.now(),
      )
    val routes =
      listOf(
        Route(
          id = RouteId("r-1"),
          agencyId = agency.agencyId,
          gtfsRouteId = "1",
          shortName = "1",
          longName = "Route 1",
          routeType = RouteType.BUS,
          active = true,
        ),
        Route(
          id = RouteId("r-2"),
          agencyId = agency.agencyId,
          gtfsRouteId = "2",
          shortName = "2",
          longName = "Route 2",
          routeType = RouteType.BUS,
          active = false,
        ),
      )

    `when`(agencyRepository.findAll()).thenReturn(listOf(agency))
    `when`(routeRepository.findByAgencyId(agency.agencyId, Pageable.unpaged()))
      .thenReturn(PageImpl(routes))
    `when`(feedQueryApi.findFeedById(FeedId("f-abc"))).thenReturn(feedDTO)

    val page: Page<*> = service.getAgencies(PageRequest.of(0, 20))
    val dto = page.content.first() as com.mobilispect.backend.agency.api.dto.AgencyDTO

    assertThat(dto.id).isEqualTo("o-123")
    assertThat(dto.routeCount).isEqualTo(2)
    assertThat(dto.activeRouteCount).isEqualTo(1)
    assertThat(dto.routesByType[RouteType.BUS]).isEqualTo(2)
    assertThat(dto.regionIds).contains("r-1")
  }

  @Test
  fun `getAgencySummary returns null when agency missing`() {
    `when`(agencyRepository.findById(AgencyId("o-missing"))).thenReturn(null)
    val result = service.getAgencySummary(AgencyId("o-missing"))
    assertThat(result).isNull()
  }

  @Test
  fun `getAgenciesByRegion aggregates agencies from feeds`() {
    val now = Instant.now()
    val feedDomain =
      Feed(
        feedId = FeedId("f-abc"),
        name = "Test Feed",
        specType = FeedSpecType.GTFS,
        downloadUrl = "https://example.com/gtfs.zip",
        currentVersionSha1 = null,
        status = FeedStatus.ACTIVE,
        regionIds = setOf(RegionId("r-1")),
        lastCheckedAt = null,
        lastUpdatedAt = null,
        lastDiscoveredAt = null,
        createdAt = now,
        updatedAt = now,
      )
    val feedDto =
      FeedDTO(
        feedId = FeedId("f-abc"),
        name = "Test Feed",
        specType = FeedSpecType.GTFS,
        downloadUrl = "https://example.com/gtfs.zip",
        currentVersionSha1 = null,
        status = FeedStatus.ACTIVE,
        regionIds = setOf(RegionId("r-1")),
        lastCheckedAt = null,
        lastUpdatedAt = null,
        lastDiscoveredAt = null,
        createdAt = now,
        updatedAt = now,
      )
    val agency =
      Agency(
        agencyId = AgencyId("o-1"),
        feedId = FeedId("f-abc"),
        gtfsAgencyId = "1",
        name = "A1",
        createdAt = Instant.now(),
        updatedAt = Instant.now(),
      )
    `when`(feedQueryApi.findFeedsByRegion(RegionId("r-1"))).thenReturn(listOf(feedDomain))
    `when`(agencyRepository.findByFeedId(any(), any())).thenReturn(PageImpl(listOf(agency)))
    `when`(routeRepository.findByAgencyId(any(), any())).thenReturn(PageImpl(emptyList()))
    `when`(routeRepository.countByAgencyId(agency.agencyId)).thenReturn(0)
    `when`(feedQueryApi.findFeedById(any())).thenReturn(feedDto)
    val page = service.getAgenciesByRegion(RegionId("r-1"), PageRequest.of(0, 20))
    assertThat(page.totalElements).isEqualTo(1)
  }
}
