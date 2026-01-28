package com.mobilispect.backend.transitanalysis.application

import com.mobilispect.backend.agency.AgencyId
import com.mobilispect.backend.agency.domain.model.Agency
import com.mobilispect.backend.agency.domain.repository.AgencyRepository
import com.mobilispect.backend.feed.FeedApi
import com.mobilispect.backend.feed.domain.model.Feed
import com.mobilispect.backend.feed.domain.model.ids.FeedId
import com.mobilispect.backend.region.RegionId
import com.mobilispect.backend.route.RouteId
import com.mobilispect.backend.route.application.CorridorQueryService
import com.mobilispect.backend.route.domain.model.CommonSection
import com.mobilispect.backend.route.domain.model.CommonSectionVariant
import com.mobilispect.backend.route.domain.model.Route
import com.mobilispect.backend.route.domain.model.RouteType
import com.mobilispect.backend.route.domain.model.RouteVariant
import com.mobilispect.backend.route.domain.model.ids.VariantHash
import com.mobilispect.backend.route.domain.repository.CommonSectionVariantRepository
import com.mobilispect.backend.route.domain.repository.RouteRepository
import com.mobilispect.backend.route.domain.repository.RouteVariantRepository
import java.util.UUID
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.Pageable

class CorridorQueryServiceTest {
  private val feedApi: FeedApi = mock(FeedApi::class.java)
  private val agencyRepository: AgencyRepository = mock(AgencyRepository::class.java)
  private val routeRepository: RouteRepository = mock(RouteRepository::class.java)
  private val routeVariantRepository: RouteVariantRepository =
    mock(RouteVariantRepository::class.java)
  private val commonSectionVariantRepository: CommonSectionVariantRepository =
    mock(CommonSectionVariantRepository::class.java)

  private val service =
    CorridorQueryService(
      feedApi,
      agencyRepository,
      routeRepository,
      routeVariantRepository,
      commonSectionVariantRepository,
    )

  @Test
  fun `returns empty list when region has no feeds`() {
    val regionId = RegionId("r-abc")
    `when`(feedApi.findFeedsByRegion(regionId)).thenReturn(emptyList())

    val result = service.getCorridorsForRegion(regionId)

    assertThat(result).isEmpty()
  }

  @Test
  fun `returns empty list when no common sections exist`() {
    val regionId = RegionId("r-abc")
    val feedId = FeedId("f-1")
    val agencyId = AgencyId("o-1")
    val routeId = RouteId("r-1")

    val feed = mock(Feed::class.java)
    `when`(feed.feedId).thenReturn(feedId)
    `when`(feedApi.findFeedsByRegion(regionId)).thenReturn(listOf(feed))

    val agency = Agency(agencyId = agencyId, feedId = feedId, name = "Agency 1")
    `when`(agencyRepository.findByFeedId(feedId, Pageable.unpaged()))
      .thenReturn(PageImpl(listOf(agency)))

    val route =
      Route(id = routeId, agencyId = agencyId, longName = "Route 1", routeType = RouteType.BUS)
    `when`(routeRepository.findByAgencyId(agencyId, Pageable.unpaged()))
      .thenReturn(PageImpl(listOf(route)))

    val variant =
      RouteVariant(
        id = VariantHash("a".repeat(64)),
        routeId = routeId,
        stopPattern = "s1|s2|s3",
        stopCount = 3,
        firstStopId = "s1",
        lastStopId = "s3",
      )
    `when`(routeVariantRepository.findByRouteId(routeId)).thenReturn(listOf(variant))
    `when`(commonSectionVariantRepository.findByVariantId(variant.id.value)).thenReturn(emptyList())

    val result = service.getCorridorsForRegion(regionId)

    assertThat(result).isEmpty()
  }

  @Test
  fun `excludes common sections with only one route`() {
    val regionId = RegionId("r-abc")
    val feedId = FeedId("f-1")
    val agencyId = AgencyId("o-1")
    val routeId = RouteId("r-1")

    val feed = mock(Feed::class.java)
    `when`(feed.feedId).thenReturn(feedId)
    `when`(feedApi.findFeedsByRegion(regionId)).thenReturn(listOf(feed))

    val agency = Agency(agencyId = agencyId, feedId = feedId, name = "Agency 1")
    `when`(agencyRepository.findByFeedId(feedId, Pageable.unpaged()))
      .thenReturn(PageImpl(listOf(agency)))

    val route =
      Route(id = routeId, agencyId = agencyId, longName = "Route 1", routeType = RouteType.BUS)
    `when`(routeRepository.findByAgencyId(agencyId, Pageable.unpaged()))
      .thenReturn(PageImpl(listOf(route)))

    val variant1 =
      RouteVariant(
        id = VariantHash("a".repeat(64)),
        routeId = routeId,
        stopPattern = "s1|s2|s3",
        stopCount = 3,
        firstStopId = "s1",
        lastStopId = "s3",
      )
    val variant2 =
      RouteVariant(
        id = VariantHash("b".repeat(64)),
        routeId = routeId,
        stopPattern = "s1|s2|s3|s4",
        stopCount = 4,
        firstStopId = "s1",
        lastStopId = "s4",
      )
    `when`(routeVariantRepository.findByRouteId(routeId)).thenReturn(listOf(variant1, variant2))

    val section =
      CommonSection(
        id = UUID.randomUUID(),
        stopPattern = "s1|s2|s3",
        stopCount = 3,
        firstStopId = "s1",
        lastStopId = "s3",
      )
    val csv1 =
      CommonSectionVariant(
        id = UUID.randomUUID(),
        commonSection = section,
        variantId = variant1.id.value,
        startSequence = 0,
        endSequence = 2,
      )
    val csv2 =
      CommonSectionVariant(
        id = UUID.randomUUID(),
        commonSection = section,
        variantId = variant2.id.value,
        startSequence = 0,
        endSequence = 2,
      )
    `when`(commonSectionVariantRepository.findByVariantId(variant1.id.value))
      .thenReturn(listOf(csv1))
    `when`(commonSectionVariantRepository.findByVariantId(variant2.id.value))
      .thenReturn(listOf(csv2))

    val result = service.getCorridorsForRegion(regionId)

    // Both variants belong to same route, so this is NOT a corridor
    assertThat(result).isEmpty()
  }

  @Test
  fun `returns corridor when two or more routes share a common section`() {
    val regionId = RegionId("r-abc")
    val feedId = FeedId("f-1")
    val agencyId = AgencyId("o-1")
    val routeId1 = RouteId("r-1")
    val routeId2 = RouteId("r-2")

    val feed = mock(Feed::class.java)
    `when`(feed.feedId).thenReturn(feedId)
    `when`(feedApi.findFeedsByRegion(regionId)).thenReturn(listOf(feed))

    val agency = Agency(agencyId = agencyId, feedId = feedId, name = "Agency 1")
    `when`(agencyRepository.findByFeedId(feedId, Pageable.unpaged()))
      .thenReturn(PageImpl(listOf(agency)))

    val route1 =
      Route(
        id = routeId1,
        agencyId = agencyId,
        shortName = "10",
        longName = "Route 10",
        routeType = RouteType.BUS,
      )
    val route2 =
      Route(
        id = routeId2,
        agencyId = agencyId,
        shortName = "20",
        longName = "Route 20",
        routeType = RouteType.BUS,
      )
    `when`(routeRepository.findByAgencyId(agencyId, Pageable.unpaged()))
      .thenReturn(PageImpl(listOf(route1, route2)))

    val variant1 =
      RouteVariant(
        id = VariantHash("a".repeat(64)),
        routeId = routeId1,
        stopPattern = "s1|s2|s3|s4",
        stopCount = 4,
        firstStopId = "s1",
        lastStopId = "s4",
      )
    val variant2 =
      RouteVariant(
        id = VariantHash("b".repeat(64)),
        routeId = routeId2,
        stopPattern = "s2|s3|s4|s5",
        stopCount = 4,
        firstStopId = "s2",
        lastStopId = "s5",
      )
    `when`(routeVariantRepository.findByRouteId(routeId1)).thenReturn(listOf(variant1))
    `when`(routeVariantRepository.findByRouteId(routeId2)).thenReturn(listOf(variant2))

    val section =
      CommonSection(
        id = UUID.randomUUID(),
        stopPattern = "s2|s3|s4",
        stopCount = 3,
        firstStopId = "s2",
        lastStopId = "s4",
      )
    val csv1 =
      CommonSectionVariant(
        id = UUID.randomUUID(),
        commonSection = section,
        variantId = variant1.id.value,
        startSequence = 1,
        endSequence = 3,
      )
    val csv2 =
      CommonSectionVariant(
        id = UUID.randomUUID(),
        commonSection = section,
        variantId = variant2.id.value,
        startSequence = 0,
        endSequence = 2,
      )
    `when`(commonSectionVariantRepository.findByVariantId(variant1.id.value))
      .thenReturn(listOf(csv1))
    `when`(commonSectionVariantRepository.findByVariantId(variant2.id.value))
      .thenReturn(listOf(csv2))

    val result = service.getCorridorsForRegion(regionId)

    assertThat(result).hasSize(1)
    assertThat(result.first().stopCount).isEqualTo(3)
    assertThat(result.first().stopPattern).isEqualTo("s2|s3|s4")
    assertThat(result.first().routes).hasSize(2)
    assertThat(result.first().routes.map { it.routeId })
      .containsExactlyInAnyOrder(routeId1.value, routeId2.value)
    assertThat(result.first().routes.map { it.shortName }).containsExactlyInAnyOrder("10", "20")
  }

  @Test
  fun `sorts corridors by number of routes descending`() {
    val regionId = RegionId("r-abc")
    val feedId = FeedId("f-1")
    val agencyId = AgencyId("o-1")
    val routeId1 = RouteId("r-1")
    val routeId2 = RouteId("r-2")
    val routeId3 = RouteId("r-3")

    val feed = mock(Feed::class.java)
    `when`(feed.feedId).thenReturn(feedId)
    `when`(feedApi.findFeedsByRegion(regionId)).thenReturn(listOf(feed))

    val agency = Agency(agencyId = agencyId, feedId = feedId, name = "Agency 1")
    `when`(agencyRepository.findByFeedId(feedId, Pageable.unpaged()))
      .thenReturn(PageImpl(listOf(agency)))

    val route1 =
      Route(
        id = routeId1,
        agencyId = agencyId,
        shortName = "10",
        longName = "Route 10",
        routeType = RouteType.BUS,
      )
    val route2 =
      Route(
        id = routeId2,
        agencyId = agencyId,
        shortName = "20",
        longName = "Route 20",
        routeType = RouteType.BUS,
      )
    val route3 =
      Route(
        id = routeId3,
        agencyId = agencyId,
        shortName = "30",
        longName = "Route 30",
        routeType = RouteType.BUS,
      )
    `when`(routeRepository.findByAgencyId(agencyId, Pageable.unpaged()))
      .thenReturn(PageImpl(listOf(route1, route2, route3)))

    val variant1 =
      RouteVariant(
        id = VariantHash("a".repeat(64)),
        routeId = routeId1,
        stopPattern = "s1|s2|s3|s4",
        stopCount = 4,
        firstStopId = "s1",
        lastStopId = "s4",
      )
    val variant2 =
      RouteVariant(
        id = VariantHash("b".repeat(64)),
        routeId = routeId2,
        stopPattern = "s2|s3|s4|s5",
        stopCount = 4,
        firstStopId = "s2",
        lastStopId = "s5",
      )
    val variant3 =
      RouteVariant(
        id = VariantHash("c".repeat(64)),
        routeId = routeId3,
        stopPattern = "s1|s2|s3|s4|s5",
        stopCount = 5,
        firstStopId = "s1",
        lastStopId = "s5",
      )
    `when`(routeVariantRepository.findByRouteId(routeId1)).thenReturn(listOf(variant1))
    `when`(routeVariantRepository.findByRouteId(routeId2)).thenReturn(listOf(variant2))
    `when`(routeVariantRepository.findByRouteId(routeId3)).thenReturn(listOf(variant3))

    // Section A: shared by routes 1, 2, 3 (3 routes)
    val sectionA =
      CommonSection(
        id = UUID.randomUUID(),
        stopPattern = "s2|s3|s4",
        stopCount = 3,
        firstStopId = "s2",
        lastStopId = "s4",
      )
    // Section B: shared by routes 1 and 3 (2 routes)
    val sectionB =
      CommonSection(
        id = UUID.randomUUID(),
        stopPattern = "s1|s2|s3",
        stopCount = 3,
        firstStopId = "s1",
        lastStopId = "s3",
      )

    val csvA1 =
      CommonSectionVariant(
        id = UUID.randomUUID(),
        commonSection = sectionA,
        variantId = variant1.id.value,
        startSequence = 1,
        endSequence = 3,
      )
    val csvA2 =
      CommonSectionVariant(
        id = UUID.randomUUID(),
        commonSection = sectionA,
        variantId = variant2.id.value,
        startSequence = 0,
        endSequence = 2,
      )
    val csvA3 =
      CommonSectionVariant(
        id = UUID.randomUUID(),
        commonSection = sectionA,
        variantId = variant3.id.value,
        startSequence = 1,
        endSequence = 3,
      )
    val csvB1 =
      CommonSectionVariant(
        id = UUID.randomUUID(),
        commonSection = sectionB,
        variantId = variant1.id.value,
        startSequence = 0,
        endSequence = 2,
      )
    val csvB3 =
      CommonSectionVariant(
        id = UUID.randomUUID(),
        commonSection = sectionB,
        variantId = variant3.id.value,
        startSequence = 0,
        endSequence = 2,
      )

    `when`(commonSectionVariantRepository.findByVariantId(variant1.id.value))
      .thenReturn(listOf(csvA1, csvB1))
    `when`(commonSectionVariantRepository.findByVariantId(variant2.id.value))
      .thenReturn(listOf(csvA2))
    `when`(commonSectionVariantRepository.findByVariantId(variant3.id.value))
      .thenReturn(listOf(csvA3, csvB3))

    val result = service.getCorridorsForRegion(regionId)

    assertThat(result).hasSize(2)
    // Section A (3 routes) should come first
    assertThat(result[0].routes).hasSize(3)
    assertThat(result[0].stopPattern).isEqualTo("s2|s3|s4")
    // Section B (2 routes) should come second
    assertThat(result[1].routes).hasSize(2)
    assertThat(result[1].stopPattern).isEqualTo("s1|s2|s3")
  }
}
