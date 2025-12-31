package com.mobilispect.backend.transitanalysis.application

import com.mobilispect.backend.feed.api.ids.GTFSRouteId
import com.mobilispect.backend.route.application.FrequencyQueryService
import com.mobilispect.backend.route.domain.model.Route
import com.mobilispect.backend.route.domain.model.RouteHourlyStat
import com.mobilispect.backend.route.domain.model.RouteType
import com.mobilispect.backend.route.domain.model.RouteVariant
import com.mobilispect.backend.route.domain.model.ServiceDayType
import com.mobilispect.backend.route.domain.model.ids.RouteId
import com.mobilispect.backend.route.domain.model.ids.VariantHash
import com.mobilispect.backend.route.domain.repository.RouteHourlyStatRepository
import com.mobilispect.backend.route.domain.repository.RouteRepository
import com.mobilispect.backend.route.domain.repository.RouteVariantRepository
import com.mobilispect.backend.route.domain.repository.StopSpacingRepository
import java.time.Instant
import java.time.LocalDate
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`

class FrequencyQueryServiceTest {
  private val routeRepository: RouteRepository = mock(RouteRepository::class.java)
  private val variantRepository: RouteVariantRepository = mock(RouteVariantRepository::class.java)
  private val stopSpacingRepository: StopSpacingRepository = mock(StopSpacingRepository::class.java)
  private val routeHourlyStatRepository: RouteHourlyStatRepository =
    mock(RouteHourlyStatRepository::class.java)
  private val service =
    FrequencyQueryService(
      routeRepository,
      variantRepository,
      stopSpacingRepository,
      routeHourlyStatRepository,
    )

  @Test
  fun `getVariantsByRoute maps variants`() {
    val route =
      Route(
        id = RouteId("r-1"),
        agencyId = com.mobilispect.backend.agency.domain.model.ids.AgencyId("o-1"),
        gtfsRouteId = GTFSRouteId("R1"),
        longName = "Route 1",
        routeType = RouteType.BUS,
        active = true,
      )
    val variant =
      RouteVariant(
        id = VariantHash("a".repeat(64)),
        routeId = route.id,
        stops = listOf("s1", "s2"),
        stopCount = 2,
        firstStopId = "s1",
        lastStopId = "s2",
      )
    `when`(variantRepository.findByRouteId(RouteId("r-1"))).thenReturn(listOf(variant))
    `when`(stopSpacingRepository.findByVariantOrderBySequence(variant.id.value))
      .thenReturn(emptyList())
    val result = service.getVariantsByRoute(RouteId("r-1"))
    assertThat(result).hasSize(1)
    assertThat(result.first().id).isEqualTo("a".repeat(64))
    assertThat(result.first().stopPattern).isEqualTo("s1|s2")
  }

  @Test
  fun `getRoute includes hourly stats when available`() {
    val route =
      Route(
        id = RouteId("r-1"),
        agencyId = com.mobilispect.backend.agency.domain.model.ids.AgencyId("o-1"),
        gtfsRouteId = GTFSRouteId("R1"),
        longName = "Route 1",
        routeType = RouteType.BUS,
        active = true,
      )
    val stat =
      RouteHourlyStat(
        routeId = route.id.value,
        dayType = ServiceDayType.WEEKDAY,
        serviceDate = LocalDate.of(2025, 1, 1),
        hourOfDay = 9,
        tripCount = 4,
        averageSpeedKph = 22.5,
        calculatedAt = Instant.now(),
        createdAt = Instant.now(),
      )
    `when`(routeRepository.findById(route.id)).thenReturn(route)
    `when`(variantRepository.findByRouteId(route.id)).thenReturn(emptyList())
    `when`(routeHourlyStatRepository.findLatestServiceDate(route.id.value))
      .thenReturn(stat.serviceDate)
    `when`(
        routeHourlyStatRepository
          .findByRouteIdAndServiceDateOrderByDayTypeAscDirectionIdAscHourOfDayAsc(
            route.id.value,
            stat.serviceDate,
          )
      )
      .thenReturn(listOf(stat))

    val result = service.getRoute(route.id)

    assertThat(result?.hourlyStats).hasSize(1)
    assertThat(result?.hourlyStats?.first()?.averageSpeedKph).isEqualTo(22.5)
  }
}
