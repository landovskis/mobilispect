package com.mobilispect.backend.agency.application

import com.mobilispect.backend.feed.model.FeedEntity
import com.mobilispect.backend.feed.model.ids.RegionId
import com.mobilispect.backend.feed.repository.FeedRepository
import com.mobilispect.backend.agency.domain.model.Agency
import com.mobilispect.backend.agency.domain.model.ids.AgencyId
import com.mobilispect.backend.agency.domain.repository.AgencyRepository
import com.mobilispect.backend.transitanalysis.domain.model.Route
import com.mobilispect.backend.transitanalysis.domain.model.RouteType
import com.mobilispect.backend.transitanalysis.domain.model.ids.RouteId
import com.mobilispect.backend.transitanalysis.domain.repository.RouteRepository
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
import java.time.Instant

@ExtendWith(MockitoExtension::class)
class AgencyQueryServiceTest {
    private val agencyRepository: AgencyRepository = mock(AgencyRepository::class.java)
    private val routeRepository: RouteRepository = mock(RouteRepository::class.java)
    private val feedRepository: FeedRepository = mock(FeedRepository::class.java)
    private val service = AgencyQueryService(agencyRepository, routeRepository, feedRepository)

    @Test
    fun `getAgencies maps agency and routes into DTO`() {
        val feed = FeedEntity(feedOnestopId = "f-abc", downloadUrl = "")
        feed.regions = mutableSetOf(
            com.mobilispect.backend.region.domain.MetropolitanRegion(
                regionOnestopId = RegionId("r-1"),
                name = "Region",
                adm0Name = "Country",
                adm1Name = "State",
                autoUpdateEnabled = true,
                createdAt = java.time.Instant.now(),
                updatedAt = java.time.Instant.now()
            )
        )
        val agency = Agency(
            agencyOnestopId = AgencyId("o-123"),
            feedId = com.mobilispect.backend.feed.model.ids.FeedId("f-abc"),
            gtfsAgencyId = "gtfs-agency",
            name = "Test Agency",
            createdAt = Instant.now(),
            updatedAt = Instant.now()
        )
        val routes = listOf(
            Route(
                id = RouteId("r-1"),
                agencyId = agency.agencyOnestopId,
                gtfsRouteId = "R1",
                shortName = "1",
                longName = "Route 1",
                routeType = RouteType.BUS,
                active = true
            ),
            Route(
                id = RouteId("r-2"),
                agencyId = agency.agencyOnestopId,
                gtfsRouteId = "R2",
                shortName = "2",
                longName = "Route 2",
                routeType = RouteType.BUS,
                active = false
            )
        )

        `when`(agencyRepository.findAll()).thenReturn(listOf(agency))
        `when`(routeRepository.findByAgencyId(agency.agencyOnestopId, Pageable.unpaged())).thenReturn(PageImpl(routes))
        `when`(feedRepository.findByFeedOnestopId("f-abc")).thenReturn(java.util.Optional.of(feed))

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
        `when`(agencyRepository.findById(AgencyId("missing"))).thenReturn(null)
        val result = service.getAgencySummary(AgencyId("missing"))
        assertThat(result).isNull()
    }

    @Test
    fun `getAgenciesByRegion aggregates agencies from feeds`() {
        val feed = FeedEntity(feedOnestopId = "f-abc", downloadUrl = "")
        val agency = Agency(
            agencyOnestopId = AgencyId("o-1"),
            feedId = com.mobilispect.backend.feed.model.ids.FeedId("f-abc"),
            gtfsAgencyId = "a1",
            name = "A1",
            createdAt = Instant.now(),
            updatedAt = Instant.now()
        )
        feed.regions = mutableSetOf(
            com.mobilispect.backend.region.domain.MetropolitanRegion(
                regionOnestopId = RegionId("r-1"),
                name = "Region",
                adm0Name = "Country",
                adm1Name = "State",
                autoUpdateEnabled = true,
                createdAt = Instant.now(),
                updatedAt = Instant.now()
            )
        )
        `when`(feedRepository.findAllByRegionRegionOnestopId(RegionId("r-1"))).thenReturn(listOf(feed))
        `when`(agencyRepository.findByFeedId(any(), any())).thenReturn(PageImpl(listOf(agency)))
        `when`(routeRepository.findByAgencyId(any(), any())).thenReturn(PageImpl(emptyList()))
        `when`(routeRepository.countByAgencyId(agency.agencyOnestopId)).thenReturn(0)
        val page = service.getAgenciesByRegion(RegionId("r-1"), PageRequest.of(0, 20))
        assertThat(page.totalElements).isEqualTo(1)
    }
}
