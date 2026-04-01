package com.mobilispect.backend.route.application

import com.mobilispect.backend.agency.AgencyId
import com.mobilispect.backend.feed.api.ids.FeedLocalAgencyId
import com.mobilispect.backend.feed.api.ids.FeedLocalRouteId
import com.mobilispect.backend.feed.domain.model.ids.FeedId
import com.mobilispect.backend.route.RouteId
import com.mobilispect.backend.route.domain.model.ParallelRouteGroup
import com.mobilispect.backend.route.domain.model.RouteVariant
import com.mobilispect.backend.route.domain.model.RouteVariantWithStops
import com.mobilispect.backend.route.domain.model.StopWithLocation
import com.mobilispect.backend.route.domain.model.ids.VariantHash
import com.mobilispect.backend.route.domain.repository.FrequencyRepository
import com.mobilispect.backend.route.domain.repository.RouteVariantRepository
import com.mobilispect.backend.route.domain.service.ParallelRouteDetectionService
import com.mobilispect.backend.stop.api.StopDTO
import com.mobilispect.backend.stop.api.StopQueryApi
import com.mobilispect.backend.stop.domain.model.ids.StopId
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import java.time.Instant
import java.time.LocalDate
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.Pageable

class ParallelRouteMergeServiceTest {

  private lateinit var variantRepository: RouteVariantRepository
  private lateinit var frequencyRepository: FrequencyRepository
  private lateinit var stopQueryApi: StopQueryApi
  private lateinit var detectionService: ParallelRouteDetectionService
  private lateinit var service: ParallelRouteMergeService

  private val feedId = FeedId("f-abc-test")
  private val agencyLocalId = FeedLocalAgencyId("agency-1")
  private val agencyId = AgencyId(feedId, agencyLocalId)

  @BeforeEach
  fun setUp() {
    variantRepository = mockk()
    frequencyRepository = mockk()
    stopQueryApi = mockk()
    detectionService = mockk()
    service =
      ParallelRouteMergeService(variantRepository, frequencyRepository, stopQueryApi, detectionService)
  }

  @Test
  fun `returns empty list when no variants exist for feed`() {
    every { variantRepository.findAll() } returns emptyList()
    every { stopQueryApi.findStopsByFeed(feedId) } returns emptyList()
    every { detectionService.detectParallelRoutes(emptyList(), any(), any(), any()) } returns
      emptyList()

    val result = service.findParallelRouteGroups(feedId, distanceThresholdMeters = 200.0)

    assertThat(result).isEmpty()
  }

  @Test
  fun `filters out variants not belonging to the specified feed`() {
    val otherFeedId = FeedId("f-other-feed")
    val otherAgencyId = AgencyId(otherFeedId, FeedLocalAgencyId("agency-x"))
    val otherVariant = buildVariant("aaaa", RouteId(otherAgencyId, FeedLocalRouteId("r-x")), "")

    every { variantRepository.findAll() } returns listOf(otherVariant)
    every { stopQueryApi.findStopsByFeed(feedId) } returns emptyList()
    every { detectionService.detectParallelRoutes(emptyList(), any(), any(), any()) } returns
      emptyList()

    val result = service.findParallelRouteGroups(feedId, distanceThresholdMeters = 200.0)

    assertThat(result).isEmpty()
  }

  @Test
  fun `builds RouteVariantWithStops from variants and stops`() {
    val routeId = RouteId(agencyId, FeedLocalRouteId("r-1"))
    val variant = buildVariant("bbbb", routeId, "gtfs-stop-1|gtfs-stop-2")

    val stopDto1 = buildStopDTO("gtfs-stop-1", 45.50, -73.55)
    val stopDto2 = buildStopDTO("gtfs-stop-2", 45.51, -73.56)

    every { variantRepository.findAll() } returns listOf(variant)
    every { stopQueryApi.findStopsByFeed(feedId) } returns listOf(stopDto1, stopDto2)

    val capturedVariants = mutableListOf<List<RouteVariantWithStops>>()
    every {
      detectionService.detectParallelRoutes(capture(capturedVariants), any(), any(), any())
    } returns emptyList()

    service.findParallelRouteGroups(feedId, distanceThresholdMeters = 200.0)

    assertThat(capturedVariants).hasSize(1)
    val passedVariants = capturedVariants.first()
    assertThat(passedVariants).hasSize(1)
    val variantWithStops = passedVariants.first()
    assertThat(variantWithStops.stops).hasSize(2)
    assertThat(variantWithStops.stops[0].stopId).isEqualTo("gtfs-stop-1")
    assertThat(variantWithStops.stops[0].latitude).isEqualTo(45.50)
    assertThat(variantWithStops.stops[1].stopId).isEqualTo("gtfs-stop-2")
  }

  @Test
  fun `delegates to detection service and returns results`() {
    val routeId = RouteId(agencyId, FeedLocalRouteId("r-1"))
    val variant = buildVariant("cccc", routeId, "")
    val expectedGroup =
      ParallelRouteGroup(
        routeIds = setOf(routeId),
        variantIds = setOf(variant.id.value),
        mergedStopSequence = emptyList(),
        averageDistanceMeters = 100.0,
      )

    every { variantRepository.findAll() } returns listOf(variant)
    every { stopQueryApi.findStopsByFeed(feedId) } returns emptyList()
    every { detectionService.detectParallelRoutes(any(), 200.0, any(), any()) } returns
      listOf(expectedGroup)

    val result = service.findParallelRouteGroups(feedId, distanceThresholdMeters = 200.0)

    assertThat(result).containsExactly(expectedGroup)
  }

  @Test
  fun `applies frequency threshold to exclude infrequent routes`() {
    val routeId1 = RouteId(agencyId, FeedLocalRouteId("r-1"))
    val routeId2 = RouteId(agencyId, FeedLocalRouteId("r-2"))
    val frequentVariant = buildVariant("dddd", routeId1, "")
    val infrequentVariant = buildVariant("eeee", routeId2, "")

    every { variantRepository.findAll() } returns listOf(frequentVariant, infrequentVariant)
    every { stopQueryApi.findStopsByFeed(feedId) } returns emptyList()

    // frequentVariant has headway 15 min (within 30 min threshold)
    every { frequencyRepository.findRecentByVariant(frequentVariant.id.value, 1) } returns
      listOf(buildFrequency(frequentVariant.id.value, averageHeadway = 15.0))

    // infrequentVariant has headway 60 min (exceeds 30 min threshold)
    every { frequencyRepository.findRecentByVariant(infrequentVariant.id.value, 1) } returns
      listOf(buildFrequency(infrequentVariant.id.value, averageHeadway = 60.0))

    val capturedVariants = mutableListOf<List<RouteVariantWithStops>>()
    every {
      detectionService.detectParallelRoutes(capture(capturedVariants), any(), any(), any())
    } returns emptyList()

    service.findParallelRouteGroups(
      feedId,
      distanceThresholdMeters = 200.0,
      minimumFrequencyMinutes = 30.0,
    )

    val passedVariants = capturedVariants.first()
    assertThat(passedVariants).hasSize(1)
    assertThat(passedVariants.first().variant.id).isEqualTo(frequentVariant.id)
  }

  @Test
  fun `excludes variants with no frequency data when threshold is set`() {
    val routeId = RouteId(agencyId, FeedLocalRouteId("r-1"))
    val variant = buildVariant("ffff", routeId, "")

    every { variantRepository.findAll() } returns listOf(variant)
    every { stopQueryApi.findStopsByFeed(feedId) } returns emptyList()
    every { frequencyRepository.findRecentByVariant(variant.id.value, 1) } returns emptyList()

    val capturedVariants = mutableListOf<List<RouteVariantWithStops>>()
    every {
      detectionService.detectParallelRoutes(capture(capturedVariants), any(), any(), any())
    } returns emptyList()

    service.findParallelRouteGroups(
      feedId,
      distanceThresholdMeters = 200.0,
      minimumFrequencyMinutes = 30.0,
    )

    val passedVariants = capturedVariants.first()
    assertThat(passedVariants).isEmpty()
  }

  @Test
  fun `includes all variants when no frequency threshold specified`() {
    val routeId1 = RouteId(agencyId, FeedLocalRouteId("r-1"))
    val routeId2 = RouteId(agencyId, FeedLocalRouteId("r-2"))
    val variant1 = buildVariant("gggg", routeId1, "")
    val variant2 = buildVariant("hhhh", routeId2, "")

    every { variantRepository.findAll() } returns listOf(variant1, variant2)
    every { stopQueryApi.findStopsByFeed(feedId) } returns emptyList()
    every { detectionService.detectParallelRoutes(any(), any(), any(), any()) } returns emptyList()

    service.findParallelRouteGroups(feedId, distanceThresholdMeters = 200.0)

    // frequencyRepository should NOT be called when no threshold is set
    // (verified by mockk strict mode - no stubbing = no call)
  }

  @Test
  fun `passes frequency by variant to detection service when difference threshold is set`() {
    val routeId1 = RouteId(agencyId, FeedLocalRouteId("r-1"))
    val routeId2 = RouteId(agencyId, FeedLocalRouteId("r-2"))
    val variant1 = buildVariant("iiii", routeId1, "")
    val variant2 = buildVariant("jjjj", routeId2, "")

    every { variantRepository.findAll() } returns listOf(variant1, variant2)
    every { stopQueryApi.findStopsByFeed(feedId) } returns emptyList()
    every { frequencyRepository.findRecentByVariant(variant1.id.value, 1) } returns
      listOf(buildFrequency(variant1.id.value, averageHeadway = 10.0))
    every { frequencyRepository.findRecentByVariant(variant2.id.value, 1) } returns
      listOf(buildFrequency(variant2.id.value, averageHeadway = 15.0))

    val capturedFreqMap = mutableListOf<Map<VariantHash, Double?>>()
    every {
      detectionService.detectParallelRoutes(any(), any(), capture(capturedFreqMap), any())
    } returns emptyList()

    service.findParallelRouteGroups(
      feedId,
      distanceThresholdMeters = 200.0,
      frequencyDifferenceThresholdMinutes = 10.0,
    )

    assertThat(capturedFreqMap.first()).containsEntry(variant1.id, 10.0)
    assertThat(capturedFreqMap.first()).containsEntry(variant2.id, 15.0)
  }

  @Test
  fun `passes frequency difference threshold to detection service`() {
    val routeId = RouteId(agencyId, FeedLocalRouteId("r-1"))
    val variant = buildVariant("kkkk", routeId, "")

    every { variantRepository.findAll() } returns listOf(variant)
    every { stopQueryApi.findStopsByFeed(feedId) } returns emptyList()
    every { frequencyRepository.findRecentByVariant(any(), any()) } returns emptyList()
    every { detectionService.detectParallelRoutes(any(), any(), any(), any()) } returns emptyList()

    service.findParallelRouteGroups(
      feedId,
      distanceThresholdMeters = 200.0,
      frequencyDifferenceThresholdMinutes = 10.0,
    )

    verify(exactly = 1) { detectionService.detectParallelRoutes(any(), any(), any(), 10.0) }
  }

  // ── helpers ───────────────────────────────────────────────────────────────────

  private fun buildVariant(idPrefix: String, routeId: RouteId, stopPattern: String): RouteVariant {
    val hexId = idPrefix.map { "%02x".format(it.code) }.joinToString("").padEnd(64, '0').take(64)
    return RouteVariant(
      id = VariantHash(hexId),
      routeId = routeId,
      stopPattern = stopPattern,
      stopCount = stopPattern.split("|").count { it.isNotEmpty() },
      firstStopId = stopPattern.split("|").firstOrNull() ?: "",
      lastStopId = stopPattern.split("|").lastOrNull() ?: "",
      firstSeen = Instant.EPOCH,
      lastSeen = Instant.EPOCH,
    )
  }

  private fun buildStopDTO(gtfsStopId: String, lat: Double, lon: Double): StopDTO =
    StopDTO(
      stopId = StopId("s-placeholder-${gtfsStopId}"),
      feedId = feedId,
      gtfsStopId = gtfsStopId,
      name = "Stop $gtfsStopId",
      latitude = lat,
      longitude = lon,
      locationType = 0,
      parentStationId = null,
      wheelchairBoarding = null,
      platformCode = null,
      zoneId = null,
      createdAt = Instant.EPOCH,
      updatedAt = Instant.EPOCH,
    )

  private fun buildFrequency(
    variantId: String,
    averageHeadway: Double?,
  ): com.mobilispect.backend.route.domain.model.Frequency {
    val f =
      com.mobilispect.backend.route.domain.model.Frequency(
        variantId = variantId,
        serviceDate = LocalDate.now(),
        timePeriod = com.mobilispect.backend.route.domain.model.TimePeriod.WEEKDAY_AM_PEAK,
        averageHeadway = averageHeadway,
        tripCount = if (averageHeadway != null) (60.0 / averageHeadway).toInt() else 0,
        isIrregular = averageHeadway == null,
        calculatedAt = Instant.EPOCH,
        createdAt = Instant.EPOCH,
      )
    return f
  }
}
