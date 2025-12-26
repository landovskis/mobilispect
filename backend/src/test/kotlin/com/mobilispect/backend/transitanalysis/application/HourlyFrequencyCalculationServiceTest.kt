package com.mobilispect.backend.transitanalysis.application

import com.mobilispect.backend.agency.domain.model.Agency
import com.mobilispect.backend.route.domain.model.Route
import com.mobilispect.backend.route.domain.model.RouteType
import com.mobilispect.backend.route.domain.model.RouteVariant
import com.mobilispect.backend.route.domain.model.ids.RouteId
import com.mobilispect.backend.route.domain.model.ids.VariantHash
import com.mobilispect.backend.route.domain.repository.RouteRepository
import com.mobilispect.backend.route.domain.repository.RouteVariantRepository
import com.mobilispect.backend.route.application.HourlyFrequencyCalculationService
import com.mobilispect.backend.route.application.HourlyFrequencyCalculationServiceImpl
import com.mobilispect.backend.transitanalysis.infrastructure.gtfs.GtfsParser
import com.mobilispect.backend.transitanalysis.infrastructure.gtfs.ParsedGtfsData
import com.mobilispect.backend.transitanalysis.infrastructure.gtfs.ParsedStopTime
import com.mobilispect.backend.transitanalysis.infrastructure.gtfs.ParsedTrip
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import java.nio.file.Paths
import java.time.LocalDate
import java.time.LocalTime

class HourlyFrequencyCalculationServiceTest {
    private lateinit var gtfsParser: GtfsParser
    private lateinit var routeRepository: RouteRepository
    private lateinit var routeVariantRepository: RouteVariantRepository
    private lateinit var agencyRepository: com.mobilispect.backend.agency.domain.repository.AgencyRepository
    private lateinit var service: HourlyFrequencyCalculationService

    private lateinit var mockAgency: Agency
    private lateinit var mockRoute: Route
    private lateinit var mockVariant1: RouteVariant
    private lateinit var mockVariant2: RouteVariant

    @BeforeEach
    fun setup() {
        gtfsParser = mock(GtfsParser::class.java)
        routeRepository = mock(RouteRepository::class.java)
        routeVariantRepository = mock(RouteVariantRepository::class.java)
        agencyRepository = mock(com.mobilispect.backend.agency.domain.repository.AgencyRepository::class.java)

        service = HourlyFrequencyCalculationServiceImpl(
            gtfsParser = gtfsParser,
            routeRepository = routeRepository,
            routeVariantRepository = routeVariantRepository,
            agencyRepository = agencyRepository,
            gtfsDownloadDirectory = "./data/gtfs"
        )

        // Setup test entities
        val feed = com.mobilispect.backend.feed.model.FeedEntity(
            feedOnestopId = "f-test",
            name = "Test Feed",
            specType = com.mobilispect.backend.feed.model.FeedSpecType.GTFS,
            downloadUrl = "https://example.com/feed.zip"
        )

        mockAgency = Agency(
            agencyOnestopId = com.mobilispect.backend.agency.domain.model.ids.AgencyId("o-test-agency"),
            feedId = com.mobilispect.backend.feed.model.ids.FeedId(feed.feedOnestopId),
            gtfsAgencyId = "A1",
            name = "Test Agency",
            active = true
        )

        mockRoute = Route(
            id = RouteId("r-route1"),
            agencyId = mockAgency.agencyOnestopId,
            gtfsRouteId = "R1",
            longName = "Test Route",
            routeType = RouteType.BUS,
            active = true
        )

        mockVariant1 = RouteVariant(
            id = VariantHash("a".repeat(64)),
            routeId = mockRoute.id,
            stopPattern = "stop1|stop2|stop3",
            stopCount = 3,
            firstStopId = "stop1",
            lastStopId = "stop3"
        )

        mockVariant2 = RouteVariant(
            id = VariantHash("b".repeat(64)),
            routeId = mockRoute.id,
            stopPattern = "stop1|stop4|stop5",
            stopCount = 3,
            firstStopId = "stop1",
            lastStopId = "stop5"
        )
    }

    @Test
    fun `calculateRouteHourlyFrequencies returns empty when route not found`() {
        `when`(routeRepository.findById(RouteId("nonexistent"))).thenReturn(null)

        val result = service.calculateRouteHourlyFrequencies(RouteId("nonexistent"), LocalDate.of(2025, 1, 15))

        assertThat(result).isEmpty()
    }

    @Test
    fun `calculateRouteHourlyFrequencies returns empty when GTFS parsing fails`() {
        `when`(routeRepository.findById(mockRoute.id)).thenReturn(mockRoute)
        `when`(agencyRepository.findById(mockAgency.agencyOnestopId)).thenReturn(mockAgency)
        `when`(routeVariantRepository.findByRouteId(mockRoute.id)).thenReturn(listOf(mockVariant1))
        `when`(gtfsParser.parse(Paths.get("./data/gtfs/f-test.zip")))
            .thenReturn(Result.failure(RuntimeException("Parse failed")))

        val result = service.calculateRouteHourlyFrequencies(mockRoute.id, LocalDate.of(2025, 1, 15))

        assertThat(result).isEmpty()
    }

    @Test
    fun `calculateRouteHourlyFrequencies returns 24 hourly records`() {
        val trips = listOf(
            createTrip("trip1", "R1", listOf(
                createStopTime("stop1", 0, LocalTime.of(8, 0)),
                createStopTime("stop2", 1, LocalTime.of(8, 5)),
                createStopTime("stop3", 2, LocalTime.of(8, 10))
            )),
            createTrip("trip2", "R1", listOf(
                createStopTime("stop1", 0, LocalTime.of(8, 15)),
                createStopTime("stop2", 1, LocalTime.of(8, 20)),
                createStopTime("stop3", 2, LocalTime.of(8, 25))
            ))
        )

        val parsedData = ParsedGtfsData(
            agencies = emptyList(),
            routes = emptyList(),
            trips = trips,
            stops = emptyList(),
            shapes = emptyMap()
        )

        `when`(routeRepository.findById(mockRoute.id)).thenReturn(mockRoute)
        `when`(agencyRepository.findById(mockAgency.agencyOnestopId)).thenReturn(mockAgency)
        `when`(routeVariantRepository.findByRouteId(mockRoute.id)).thenReturn(listOf(mockVariant1))
        `when`(gtfsParser.parse(Paths.get("./data/gtfs/f-test.zip"))).thenReturn(Result.success(parsedData))

        val result = service.calculateRouteHourlyFrequencies(mockRoute.id, LocalDate.of(2025, 1, 15))

        assertThat(result).hasSize(24)
        assertThat(result.map { it.hourOfDay }).containsExactly(0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20, 21, 22, 23)
    }

    @Test
    fun `calculateRouteHourlyFrequencies calculates headways correctly for hour with trips`() {
        val trips = listOf(
            createTrip("trip1", "R1", listOf(
                createStopTime("stop1", 0, LocalTime.of(8, 0)),
                createStopTime("stop2", 1, LocalTime.of(8, 5)),
                createStopTime("stop3", 2, LocalTime.of(8, 10))
            )),
            createTrip("trip2", "R1", listOf(
                createStopTime("stop1", 0, LocalTime.of(8, 15)),
                createStopTime("stop2", 1, LocalTime.of(8, 20)),
                createStopTime("stop3", 2, LocalTime.of(8, 25))
            )),
            createTrip("trip3", "R1", listOf(
                createStopTime("stop1", 0, LocalTime.of(8, 30)),
                createStopTime("stop2", 1, LocalTime.of(8, 35)),
                createStopTime("stop3", 2, LocalTime.of(8, 40))
            ))
        )

        val parsedData = ParsedGtfsData(
            agencies = emptyList(),
            routes = emptyList(),
            trips = trips,
            stops = emptyList(),
            shapes = emptyMap()
        )

        `when`(routeRepository.findById(mockRoute.id)).thenReturn(mockRoute)
        `when`(agencyRepository.findById(mockAgency.agencyOnestopId)).thenReturn(mockAgency)
        `when`(routeVariantRepository.findByRouteId(mockRoute.id)).thenReturn(listOf(mockVariant1))
        `when`(gtfsParser.parse(Paths.get("./data/gtfs/f-test.zip"))).thenReturn(Result.success(parsedData))

        val result = service.calculateRouteHourlyFrequencies(mockRoute.id, LocalDate.of(2025, 1, 15))

        val hour8 = result.find { it.hourOfDay == 8 }
        assertThat(hour8).isNotNull
        assertThat(hour8!!.tripCount).isEqualTo(3)
        assertThat(hour8.averageHeadwayMinutes).isEqualTo(15.0)
        assertThat(hour8.minHeadwayMinutes).isEqualTo(15.0)
        assertThat(hour8.maxHeadwayMinutes).isEqualTo(15.0)
        assertThat(hour8.isIrregular).isFalse()
        assertThat(hour8.variantCount).isEqualTo(1)
    }

    @Test
    fun `calculateRouteHourlyFrequencies sets tripCount to 0 for hours without service`() {
        val trips = listOf(
            createTrip("trip1", "R1", listOf(
                createStopTime("stop1", 0, LocalTime.of(8, 0)),
                createStopTime("stop2", 1, LocalTime.of(8, 5)),
                createStopTime("stop3", 2, LocalTime.of(8, 10))
            ))
        )

        val parsedData = ParsedGtfsData(
            agencies = emptyList(),
            routes = emptyList(),
            trips = trips,
            stops = emptyList(),
            shapes = emptyMap()
        )

        `when`(routeRepository.findById(mockRoute.id)).thenReturn(mockRoute)
        `when`(agencyRepository.findById(mockAgency.agencyOnestopId)).thenReturn(mockAgency)
        `when`(routeVariantRepository.findByRouteId(mockRoute.id)).thenReturn(listOf(mockVariant1))
        `when`(gtfsParser.parse(Paths.get("./data/gtfs/f-test.zip"))).thenReturn(Result.success(parsedData))

        val result = service.calculateRouteHourlyFrequencies(mockRoute.id, LocalDate.of(2025, 1, 15))

        val hour9 = result.find { it.hourOfDay == 9 }
        assertThat(hour9).isNotNull
        assertThat(hour9!!.tripCount).isEqualTo(0)
        assertThat(hour9.averageHeadwayMinutes).isNull()
        assertThat(hour9.minHeadwayMinutes).isNull()
        assertThat(hour9.maxHeadwayMinutes).isNull()
        assertThat(hour9.isIrregular).isFalse()
    }

    @Test
    fun `calculateRouteHourlyFrequencies aggregates multiple variants`() {
        val trips = listOf(
            // Variant 1 trips
            createTrip("trip1", "R1", listOf(
                createStopTime("stop1", 0, LocalTime.of(8, 0)),
                createStopTime("stop2", 1, LocalTime.of(8, 5)),
                createStopTime("stop3", 2, LocalTime.of(8, 10))
            )),
            // Variant 2 trips
            createTrip("trip2", "R1", listOf(
                createStopTime("stop1", 0, LocalTime.of(8, 10)),
                createStopTime("stop4", 1, LocalTime.of(8, 15)),
                createStopTime("stop5", 2, LocalTime.of(8, 20))
            ))
        )

        val parsedData = ParsedGtfsData(
            agencies = emptyList(),
            routes = emptyList(),
            trips = trips,
            stops = emptyList(),
            shapes = emptyMap()
        )

        `when`(routeRepository.findById(mockRoute.id)).thenReturn(mockRoute)
        `when`(agencyRepository.findById(mockAgency.agencyOnestopId)).thenReturn(mockAgency)
        `when`(routeVariantRepository.findByRouteId(mockRoute.id)).thenReturn(listOf(mockVariant1, mockVariant2))
        `when`(gtfsParser.parse(Paths.get("./data/gtfs/f-test.zip"))).thenReturn(Result.success(parsedData))

        val result = service.calculateRouteHourlyFrequencies(mockRoute.id, LocalDate.of(2025, 1, 15))

        val hour8 = result.find { it.hourOfDay == 8 }
        assertThat(hour8).isNotNull
        assertThat(hour8!!.tripCount).isEqualTo(2)
        assertThat(hour8.variantCount).isEqualTo(2)
    }

    @Test
    fun `calculateVariantHourlyFrequencies returns empty when variant not found`() {
        val nonexistentHash = VariantHash("9".repeat(64))
        `when`(routeVariantRepository.findById(nonexistentHash)).thenReturn(null)

        val result = service.calculateVariantHourlyFrequencies(
            nonexistentHash,
            LocalDate.of(2025, 1, 15)
        )

        assertThat(result).isEmpty()
    }

    @Test
    fun `calculateVariantHourlyFrequencies returns 24 hourly records for variant`() {
        val trips = listOf(
            createTrip("trip1", "R1", listOf(
                createStopTime("stop1", 0, LocalTime.of(8, 0)),
                createStopTime("stop2", 1, LocalTime.of(8, 5)),
                createStopTime("stop3", 2, LocalTime.of(8, 10))
            ))
        )

        val parsedData = ParsedGtfsData(
            agencies = emptyList(),
            routes = emptyList(),
            trips = trips,
            stops = emptyList(),
            shapes = emptyMap()
        )

        `when`(routeVariantRepository.findById(mockVariant1.id)).thenReturn(mockVariant1)
        `when`(routeRepository.findById(mockRoute.id)).thenReturn(mockRoute)
        `when`(agencyRepository.findById(mockAgency.agencyOnestopId)).thenReturn(mockAgency)
        `when`(gtfsParser.parse(Paths.get("./data/gtfs/f-test.zip"))).thenReturn(Result.success(parsedData))

        val result = service.calculateVariantHourlyFrequencies(mockVariant1.id, LocalDate.of(2025, 1, 15))

        assertThat(result).hasSize(24)
        assertThat(result.map { it.hourOfDay }).containsExactly(0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20, 21, 22, 23)
    }

    @Test
    fun `calculateVariantHourlyFrequencies detects irregular schedules`() {
        val trips = listOf(
            createTrip("trip1", "R1", listOf(
                createStopTime("stop1", 0, LocalTime.of(8, 0)),
                createStopTime("stop2", 1, LocalTime.of(8, 5)),
                createStopTime("stop3", 2, LocalTime.of(8, 10))
            )),
            createTrip("trip2", "R1", listOf(
                createStopTime("stop1", 0, LocalTime.of(8, 10)),
                createStopTime("stop2", 1, LocalTime.of(8, 15)),
                createStopTime("stop3", 2, LocalTime.of(8, 20))
            )),
            createTrip("trip3", "R1", listOf(
                createStopTime("stop1", 0, LocalTime.of(8, 50)), // Large gap
                createStopTime("stop2", 1, LocalTime.of(8, 55)),
                createStopTime("stop3", 2, LocalTime.of(9, 0))
            ))
        )

        val parsedData = ParsedGtfsData(
            agencies = emptyList(),
            routes = emptyList(),
            trips = trips,
            stops = emptyList(),
            shapes = emptyMap()
        )

        `when`(routeVariantRepository.findById(mockVariant1.id)).thenReturn(mockVariant1)
        `when`(routeRepository.findById(mockRoute.id)).thenReturn(mockRoute)
        `when`(agencyRepository.findById(mockAgency.agencyOnestopId)).thenReturn(mockAgency)
        `when`(gtfsParser.parse(Paths.get("./data/gtfs/f-test.zip"))).thenReturn(Result.success(parsedData))

        val result = service.calculateVariantHourlyFrequencies(mockVariant1.id, LocalDate.of(2025, 1, 15))

        val hour8 = result.find { it.hourOfDay == 8 }
        assertThat(hour8).isNotNull
        assertThat(hour8!!.isIrregular).isTrue() // abs(40 - 10) = 30 > average (25)
    }

    private fun createTrip(tripId: String, routeId: String, stopTimes: List<ParsedStopTime>): ParsedTrip {
        return ParsedTrip(
            tripId = tripId,
            routeId = routeId,
            headsign = "Downtown",
            directionId = 0,
            shapeId = null,
            stopTimes = stopTimes
        )
    }

    private fun createStopTime(stopId: String, sequence: Int, departureTime: LocalTime): ParsedStopTime {
        return ParsedStopTime(
            stopId = stopId,
            stopSequence = sequence,
            departureTime = departureTime,
            shapeDistTraveledKm = null
        )
    }
}
