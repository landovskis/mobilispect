package com.mobilispect.backend

import com.mobilispect.backend.infastructure.Stop
import com.mobilispect.backend.infastructure.StopRepository
import com.mobilispect.backend.schedule.ImportScheduledFeedsService
import com.mobilispect.backend.schedule.Route
import com.mobilispect.backend.schedule.RouteRepository
import com.mobilispect.backend.schedule.ScheduledStopRepository
import com.mobilispect.backend.schedule.ScheduledTripRepository
import com.mobilispect.backend.schedule.ScheduledFeed
import com.mobilispect.backend.schedule.ScheduledStop
import com.mobilispect.backend.schedule.ScheduledStopDataSource
import com.mobilispect.backend.schedule.ScheduledTrip
import com.mobilispect.backend.schedule.ScheduledTripDataSource
import com.mobilispect.backend.util.ArchiveExtractor
import com.mobilispect.backend.schedule.download.Downloader
import com.mobilispect.backend.schedule.gtfs.GTFSRouteDataSource
import com.mobilispect.backend.schedule.gtfs.StubAgencyIDDataSource
import com.mobilispect.backend.schedule.route.RouteDataSource
import com.mobilispect.backend.schedule.stop.StopDataSource
import com.mobilispect.backend.util.DateTimeOffset
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ContextConfiguration
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import java.io.IOException
import java.time.LocalDate

@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
)
@Testcontainers
@Suppress("LargeClass")
@ContextConfiguration(initializers = [ImportScheduledFeedsServiceTest.Companion.DBInitializer::class])
internal class ImportScheduledFeedsServiceTest {
    companion object {
        @Container
        @JvmStatic
        val container = createMongoDBContainer()

        class DBInitializer : MongoDBInitializer(container)

        val version = "d89aa5de884111e4b6a9365220ded9f746ef2dbf"
    }

    @Autowired
    lateinit var downloader: Downloader

    @Autowired
    lateinit var archiveExtractor: ArchiveExtractor

    @Autowired
    lateinit var regionRepository: RegionRepository

    @Autowired
    lateinit var feedRepository: FeedRepository

    @Autowired
    lateinit var feedVersionRepository: FeedVersionRepository

    @Autowired
    lateinit var agencyRepository: AgencyRepository

    @Autowired
    lateinit var routeRepository: RouteRepository

    @Autowired
    lateinit var stopRepository: StopRepository

    @Autowired
    lateinit var scheduledTripRepository: ScheduledTripRepository

    @Autowired
    lateinit var scheduledStopRepository: ScheduledStopRepository

    @Autowired
    lateinit var agencyDataSource: AgencyDataSource

    @Autowired
    lateinit var routeDataSource: RouteDataSource

    @Autowired
    lateinit var stopDataSource: StopDataSource

    @Autowired
    lateinit var tripDataSource: ScheduledTripDataSource

    @Autowired
    lateinit var scheduledStopDataSource: ScheduledStopDataSource

    @Autowired
    lateinit var resourceDownloader: ResourceDownloader

    @Test
    fun unableToRetrieveFeeds() = runTest {
        val networkDataSource = object : FeedDataSource {
            override fun feeds(region: String): Collection<Result<ScheduledFeed>> =
                listOf(Result.failure(IOException("Couldn't connect")))
        }
        val subject = ImportScheduledFeedsService(
            feedDataSource = networkDataSource,
            feedRepository = feedRepository,
            feedVersionRepository = feedVersionRepository,
            downloader = downloader,
            archiveExtractor = archiveExtractor,
            regionRepository = regionRepository,
            agencyRepository = agencyRepository,
            routeRepository = routeRepository,
            stopRepository = stopRepository,
            scheduledTripRepository = scheduledTripRepository,
            scheduledStopRepository = scheduledStopRepository,
            agencyDataSource = agencyDataSource,
            routeDataSource = routeDataSource,
            stopDataSource = stopDataSource,
            scheduledTripDataSource = tripDataSource,
            scheduledStopDataSource = scheduledStopDataSource,
        )

        subject()

        assertThat(feedRepository.findAll()).isEmpty()
        assertThat(feedVersionRepository.findAll()).isEmpty()
        assertThat(agencyRepository.findAll()).isEmpty()
        assertThat(routeRepository.findAll()).isEmpty()
        assertThat(stopRepository.findAll()).isEmpty()
        assertThat(scheduledTripRepository.findAll()).isEmpty()
        assertThat(scheduledStopRepository.findAll()).isEmpty()
    }

    @Test
    fun unableToDownloadFeed() = runTest {
        val mockServer = MockWebServer()
        mockServer.enqueue(MockResponse())
        mockServer.start()

        val networkDataSource = object : FeedDataSource {
            override fun feeds(region: String): Collection<Result<ScheduledFeed>> = listOf(
                Result.success(
                    ScheduledFeed(
                        feed = Feed(
                            uid = "f-f256-exo~citlapresquîle", url = mockServer.url("").toString()
                        ), version = FeedVersion(
                            uid = "d89aa5de884111e4b6a9365220ded9f746ef2dbf",
                            feedID = "f-f256-exo~citlapresquîle",
                            startsOn = LocalDate.of(2022, 11, 23),
                            endsOn = LocalDate.of(2023, 6, 25)
                        )
                    )
                )
            )
        }
        mockServer.shutdown()
        val subject = ImportScheduledFeedsService(
            feedDataSource = networkDataSource,
            feedRepository = feedRepository,
            feedVersionRepository = feedVersionRepository,
            downloader = resourceDownloader,
            archiveExtractor = archiveExtractor,
            regionRepository = regionRepository,
            agencyRepository = agencyRepository,
            routeRepository = routeRepository,
            stopRepository = stopRepository,
            scheduledTripRepository = scheduledTripRepository,
            scheduledStopRepository = scheduledStopRepository,
            agencyDataSource = agencyDataSource,
            routeDataSource = routeDataSource,
            stopDataSource = stopDataSource,
            scheduledTripDataSource = tripDataSource,
            scheduledStopDataSource = scheduledStopDataSource,
        )

        subject()

        assertThat(feedRepository.findAll()).isEmpty()
        assertThat(feedVersionRepository.findAll()).isEmpty()
        assertThat(agencyRepository.findAll()).isEmpty()
        assertThat(routeRepository.findAll()).isEmpty()
        assertThat(stopRepository.findAll()).isEmpty()
        assertThat(scheduledTripRepository.findAll()).isEmpty()
        assertThat(scheduledStopRepository.findAll()).isEmpty()
    }

    @Test
    fun addsFeedAndVersionIfNone() = runTest {
        val region = Region(uid = "reg-f25-mtl", name = "Montréal")
        regionRepository.save(region)

        val feedDataSource = object : FeedDataSource {
            override fun feeds(region: String): Collection<Result<ScheduledFeed>> = listOf(
                Result.success(
                    ScheduledFeed(
                        feed = Feed(
                            uid = "f-f256-exo~citlapresquîle",
                            url = "classpath:exopi-gtfs-d89aa5de884111e4b6a9365220ded9f746ef2dbf.zip"
                        ), version = FeedVersion(
                            uid = "d89aa5de884111e4b6a9365220ded9f746ef2dbf",
                            feedID = "f-f256-exo~citlapresquîle",
                            startsOn = LocalDate.of(2022, 11, 23),
                            endsOn = LocalDate.of(2023, 6, 25)
                        )
                    )
                )
            )
        }

        val agencyIDDataSource = StubAgencyIDDataSource(
            mapOf(
                "CITPI" to "o-f256-exo~citlapresquîle"
            )
        )
        val agencyDataSource = GTFSAgencyDataSource(agencyIDDataSource)

        val routeIDDataSource = StubRouteIDDataSource(mapOf(
            "1" to "r-f2566-1",
            "40" to "r-f256-40",
            "115" to "r-f2565-115",
        ))
        val routeDataSource = GTFSRouteDataSource(agencyIDDataSource, routeIDDataSource)

        val subject = ImportScheduledFeedsService(
            feedDataSource = feedDataSource,
            feedRepository = feedRepository,
            feedVersionRepository = feedVersionRepository,
            downloader = resourceDownloader,
            archiveExtractor = archiveExtractor,
            regionRepository = regionRepository,
            agencyRepository = agencyRepository,
            routeRepository = routeRepository,
            stopRepository = stopRepository,
            scheduledTripRepository = scheduledTripRepository,
            scheduledStopRepository = scheduledStopRepository,
            agencyDataSource = agencyDataSource,
            routeDataSource = routeDataSource,
            stopDataSource = stopDataSource,
            scheduledTripDataSource = tripDataSource,
            scheduledStopDataSource = scheduledStopDataSource,
        )

        subject()

        importedAllFeeds()
        importedAllAgencies()
        importedAllRoutes()
        importedAllStops()
        importedAllTrips()
        importedAllStopTimes()
    }

    private fun importedAllFeeds() {
        val actualFeeds = feedRepository.findAll()
        assertThat(actualFeeds).contains(
            Feed(
                uid = "f-f256-exo~citlapresquîle", url = "classpath:exopi-gtfs-d89aa5de884111e4b6a9365220ded9f746ef2dbf.zip"
            )
        )

        val actualFeedVersions = feedVersionRepository.findAll()
        assertThat(actualFeedVersions).contains(
            FeedVersion(
                uid = "d89aa5de884111e4b6a9365220ded9f746ef2dbf",
                feedID = "f-f256-exo~citlapresquîle",
                startsOn = LocalDate.of(2022, 11, 23),
                endsOn = LocalDate.of(2023, 6, 25)
            )
        )
    }

    private fun importedAllAgencies() {
        val agencies = agencyRepository.findAll()
        assertThat(agencies).contains(
            Agency(
                uid = "o-f256-exo~citlapresquîle",
                localID = "CITPI",
                name = "exo-La Presqu'île",
                versions = listOf(version)
            )
        )
        assertThat(agencies).hasSize(1)
    }

    private fun importedAllRoutes() {
        val routes = routeRepository.findAll()
        assertThat(routes).contains(
            Route(
                uid = "r-f2566-1",
                localID = "1",
                shortName = "1",
                longName = "Gare Vaudreuil/Parc Industriel/Seigneurie",
                agencyID = "o-f256-exo~citlapresquîle",
                versions = listOf(version)
            ),
        )

        assertThat(routes).hasSize(3)
    }

    private fun importedAllStops() {
        val stops = stopRepository.findAll()

        assertThat(stops).contains(
            Stop(
                uid = "s-f256742cpt-joseph~carrier~f~x~tessier",
                localID = "72703",
                name = "Joseph-Carrier / F.-X.-Tessier",
                versions = listOf(version)
            )
        )
        assertThat(stops).hasSize(866)
    }

    @Suppress("LongMethod")
    private fun importedAllTrips() {
        val trips = scheduledTripRepository.findAll()

        assertThat(trips).contains(
            ScheduledTrip(
                uid = "3282393-PI-A25-PI_GTFS-Samedi-0", routeID = "r-f2565-115", dates = listOf(
                    LocalDate.of(2025, 8, 23),
                    LocalDate.of(2025, 8, 30),
                    LocalDate.of(2025, 9, 6),
                    LocalDate.of(2025, 9, 13),
                    LocalDate.of(2025, 9, 20),
                    LocalDate.of(2025, 9, 27),
                    LocalDate.of(2025, 10, 4),
                    LocalDate.of(2025, 10, 11),
                    LocalDate.of(2025, 10, 18),
                    LocalDate.of(2025, 10, 25),
                    LocalDate.of(2025, 11, 1),
                    LocalDate.of(2025, 11, 8),
                    LocalDate.of(2025, 11, 15),
                    LocalDate.of(2025, 11, 22),
                    LocalDate.of(2025, 11, 29),
                    LocalDate.of(2025, 12, 6),
                    LocalDate.of(2025, 12, 13),
                    LocalDate.of(2025, 12, 20),
                    LocalDate.of(2025, 12, 27),
                    LocalDate.of(2026, 1, 3)
                ), direction = "Terminus Vaudreuil", versions = listOf("d89aa5de884111e4b6a9365220ded9f746ef2dbf")
            )
        )
        assertThat(trips).hasSize(465)
    }

    @Suppress("LongMethod")
    private fun importedAllStopTimes() {
        val scheduledStops = scheduledStopRepository.findAll()
        assertThat(scheduledStops).contains(
            ScheduledStop(
                tripID = "3281905-PI-A22-PI_GTFS-Semaine-01",
                stopID = "72986",
                departsAt = DateTimeOffset(hours = 6, minutes = 37),
                arrivesAt = DateTimeOffset(hours = 6, minutes = 37),
                stopSequence = 1,
                versions = listOf(version)
            )
        )
        assertThat(scheduledStops).contains(
            ScheduledStop(
                tripID = "3281905-PI-A22-PI_GTFS-Semaine-01",
                stopID = "72748",
                departsAt = DateTimeOffset(hours = 6, minutes = 37, seconds = 28),
                arrivesAt = DateTimeOffset(hours = 6, minutes = 37, seconds = 28),
                stopSequence = 2,
                versions = listOf(version)
            )
        )
        assertThat(scheduledStops).contains(
            ScheduledStop(
                tripID = "3281905-PI-A22-PI_GTFS-Semaine-01",
                stopID = "72960",
                departsAt = DateTimeOffset(hours = 6, minutes = 37, seconds = 38),
                arrivesAt = DateTimeOffset(hours = 6, minutes = 37, seconds = 38),
                stopSequence = 3,
                versions = listOf(version)
            )
        )
        assertThat(scheduledStops).contains(
            ScheduledStop(
                tripID = "3281905-PI-A22-PI_GTFS-Semaine-01",
                stopID = "72711",
                departsAt = DateTimeOffset(hours = 6, minutes = 37, seconds = 51),
                arrivesAt = DateTimeOffset(hours = 6, minutes = 37, seconds = 51),
                stopSequence = 4,
                versions = listOf(version)
            )
        )
        assertThat(scheduledStops).contains(
            ScheduledStop(
                tripID = "3281905-PI-A22-PI_GTFS-Semaine-01",
                stopID = "72618",
                departsAt = DateTimeOffset(hours = 6, minutes = 38, seconds = 0),
                arrivesAt = DateTimeOffset(hours = 6, minutes = 38, seconds = 0),
                stopSequence = 5,
                versions = listOf(version)
            )
        )
        assertThat(scheduledStops).contains(
            ScheduledStop(
                tripID = "3281905-PI-A22-PI_GTFS-Semaine-01",
                stopID = "72828",
                departsAt = DateTimeOffset(hours = 6, minutes = 42, seconds = 0),
                arrivesAt = DateTimeOffset(hours = 6, minutes = 42, seconds = 0),
                stopSequence = 6,
                versions = listOf(version)
            )
        )
        assertThat(scheduledStops).contains(
            ScheduledStop(
                tripID = "3281905-PI-A22-PI_GTFS-Semaine-01",
                stopID = "72698",
                departsAt = DateTimeOffset(hours = 6, minutes = 42, seconds = 26),
                arrivesAt = DateTimeOffset(hours = 6, minutes = 42, seconds = 26),
                stopSequence = 7,
                versions = listOf(version)
            )
        )
        assertThat(scheduledStops).contains(
            ScheduledStop(
                tripID = "3281905-PI-A22-PI_GTFS-Semaine-01",
                stopID = "72702",
                departsAt = DateTimeOffset(hours = 6, minutes = 42, seconds = 55),
                arrivesAt = DateTimeOffset(hours = 6, minutes = 42, seconds = 55),
                stopSequence = 8,
                versions = listOf(version)
            )
        )
        assertThat(scheduledStops).contains(
            ScheduledStop(
                tripID = "3281905-PI-A22-PI_GTFS-Semaine-01",
                stopID = "72704",
                departsAt = DateTimeOffset(hours = 6, minutes = 43, seconds = 26),
                arrivesAt = DateTimeOffset(hours = 6, minutes = 43, seconds = 26),
                stopSequence = 9,
                versions = listOf(version)
            )
        )
        assertThat(scheduledStops).contains(
            ScheduledStop(
                tripID = "3281905-PI-A22-PI_GTFS-Semaine-01",
                stopID = "72696",
                departsAt = DateTimeOffset(hours = 6, minutes = 43, seconds = 57),
                arrivesAt = DateTimeOffset(hours = 6, minutes = 43, seconds = 57),
                stopSequence = 10,
                versions = listOf(version)
            )
        )
        assertThat(scheduledStops).contains(
            ScheduledStop(
                tripID = "3281905-PI-A22-PI_GTFS-Semaine-01",
                stopID = "72705",
                departsAt = DateTimeOffset(hours = 6, minutes = 44, seconds = 15),
                arrivesAt = DateTimeOffset(hours = 6, minutes = 44, seconds = 15),
                stopSequence = 11,
                versions = listOf(version)
            )
        )
        assertThat(scheduledStops).contains(
            ScheduledStop(
                tripID = "3281905-PI-A22-PI_GTFS-Semaine-01",
                stopID = "72707",
                departsAt = DateTimeOffset(hours = 6, minutes = 44, seconds = 55),
                arrivesAt = DateTimeOffset(hours = 6, minutes = 44, seconds = 55),
                stopSequence = 12,
                versions = listOf(version)
            )
        )
        assertThat(scheduledStops).contains(
            ScheduledStop(
                tripID = "3281905-PI-A22-PI_GTFS-Semaine-01",
                stopID = "72710",
                departsAt = DateTimeOffset(hours = 6, minutes = 45, seconds = 42),
                arrivesAt = DateTimeOffset(hours = 6, minutes = 45, seconds = 42),
                stopSequence = 13,
                versions = listOf(version)
            )
        )
        assertThat(scheduledStops).contains(
            ScheduledStop(
                tripID = "3281905-PI-A22-PI_GTFS-Semaine-01",
                stopID = "72916",
                departsAt = DateTimeOffset(hours = 6, minutes = 46, seconds = 4),
                arrivesAt = DateTimeOffset(hours = 6, minutes = 46, seconds = 4),
                stopSequence = 14,
                versions = listOf(version)
            )
        )
        assertThat(scheduledStops).contains(
            ScheduledStop(
                tripID = "3281905-PI-A22-PI_GTFS-Semaine-01",
                stopID = "72901",
                departsAt = DateTimeOffset(hours = 6, minutes = 47, seconds = 25),
                arrivesAt = DateTimeOffset(hours = 6, minutes = 47, seconds = 25),
                stopSequence = 15,
                versions = listOf(version)
            )
        )
        assertThat(scheduledStops).contains(
            ScheduledStop(
                tripID = "3281905-PI-A22-PI_GTFS-Semaine-01",
                stopID = "72577",
                departsAt = DateTimeOffset(hours = 6, minutes = 48, seconds = 0),
                arrivesAt = DateTimeOffset(hours = 6, minutes = 48, seconds = 0),
                stopSequence = 16,
                versions = listOf(version)
            )
        )
        assertThat(scheduledStops).contains(
            ScheduledStop(
                tripID = "3281905-PI-A22-PI_GTFS-Semaine-01",
                stopID = "72579",
                departsAt = DateTimeOffset(hours = 6, minutes = 48, seconds = 11),
                arrivesAt = DateTimeOffset(hours = 6, minutes = 48, seconds = 11),
                stopSequence = 17,
                versions = listOf(version)
            )
        )
        assertThat(scheduledStops).contains(
            ScheduledStop(
                tripID = "3281905-PI-A22-PI_GTFS-Semaine-01",
                stopID = "72581",
                departsAt = DateTimeOffset(hours = 6, minutes = 48, seconds = 36),
                arrivesAt = DateTimeOffset(hours = 6, minutes = 48, seconds = 36),
                stopSequence = 18,
                versions = listOf(version)
            )
        )
        assertThat(scheduledStops).contains(
            ScheduledStop(
                tripID = "3281905-PI-A22-PI_GTFS-Semaine-01",
                stopID = "72567",
                departsAt = DateTimeOffset(hours = 6, minutes = 49, seconds = 1),
                arrivesAt = DateTimeOffset(hours = 6, minutes = 49, seconds = 1),
                stopSequence = 19,
                versions = listOf(version)
            )
        )
        assertThat(scheduledStops).contains(
            ScheduledStop(
                tripID = "3281905-PI-A22-PI_GTFS-Semaine-01",
                stopID = "72568",
                departsAt = DateTimeOffset(hours = 6, minutes = 49, seconds = 30),
                arrivesAt = DateTimeOffset(hours = 6, minutes = 49, seconds = 30),
                stopSequence = 20,
                versions = listOf(version)
            )
        )
        assertThat(scheduledStops).contains(
            ScheduledStop(
                tripID = "3281905-PI-A22-PI_GTFS-Semaine-01",
                stopID = "72570",
                departsAt = DateTimeOffset(hours = 6, minutes = 49, seconds = 42),
                arrivesAt = DateTimeOffset(hours = 6, minutes = 49, seconds = 42),
                stopSequence = 21,
                versions = listOf(version)
            )
        )
        assertThat(scheduledStops).contains(
            ScheduledStop(
                tripID = "3281905-PI-A22-PI_GTFS-Semaine-01",
                stopID = "72626",
                departsAt = DateTimeOffset(hours = 6, minutes = 50, seconds = 8),
                arrivesAt = DateTimeOffset(hours = 6, minutes = 50, seconds = 8),
                stopSequence = 22,
                versions = listOf(version)
            )
        )
        assertThat(scheduledStops).contains(
            ScheduledStop(
                tripID = "3281905-PI-A22-PI_GTFS-Semaine-01",
                stopID = "72722",
                departsAt = DateTimeOffset(hours = 6, minutes = 50, seconds = 36),
                arrivesAt = DateTimeOffset(hours = 6, minutes = 50, seconds = 36),
                stopSequence = 23,
                versions = listOf(version)
            )
        )
        assertThat(scheduledStops).contains(
            ScheduledStop(
                tripID = "3281905-PI-A22-PI_GTFS-Semaine-01",
                stopID = "72720",
                departsAt = DateTimeOffset(hours = 6, minutes = 51, seconds = 6),
                arrivesAt = DateTimeOffset(hours = 6, minutes = 51, seconds = 6),
                stopSequence = 24,
                versions = listOf(version)
            )
        )
        assertThat(scheduledStops).contains(
            ScheduledStop(
                tripID = "3281905-PI-A22-PI_GTFS-Semaine-01",
                stopID = "72789",
                departsAt = DateTimeOffset(hours = 6, minutes = 51, seconds = 26),
                arrivesAt = DateTimeOffset(hours = 6, minutes = 51, seconds = 26),
                stopSequence = 25,
                versions = listOf(version)
            )
        )
        assertThat(scheduledStops).contains(
            ScheduledStop(
                tripID = "3281905-PI-A22-PI_GTFS-Semaine-01",
                stopID = "72571",
                departsAt = DateTimeOffset(hours = 6, minutes = 52, seconds = 0),
                arrivesAt = DateTimeOffset(hours = 6, minutes = 52, seconds = 0),
                stopSequence = 26,
                versions = listOf(version)
            )
        )
        assertThat(scheduledStops).contains(
            ScheduledStop(
                tripID = "3281905-PI-A22-PI_GTFS-Semaine-01",
                stopID = "72919",
                departsAt = DateTimeOffset(hours = 6, minutes = 52, seconds = 20),
                arrivesAt = DateTimeOffset(hours = 6, minutes = 52, seconds = 20),
                stopSequence = 27,
                versions = listOf(version)
            )
        )
        assertThat(scheduledStops).contains(
            ScheduledStop(
                tripID = "3281905-PI-A22-PI_GTFS-Semaine-01",
                stopID = "72801",
                departsAt = DateTimeOffset(hours = 6, minutes = 52, seconds = 35),
                arrivesAt = DateTimeOffset(hours = 6, minutes = 52, seconds = 35),
                stopSequence = 28,
                versions = listOf(version)
            )
        )
        assertThat(scheduledStops).contains(
            ScheduledStop(
                tripID = "3281905-PI-A22-PI_GTFS-Semaine-01",
                stopID = "72585",
                departsAt = DateTimeOffset(hours = 6, minutes = 52, seconds = 54),
                arrivesAt = DateTimeOffset(hours = 6, minutes = 52, seconds = 54),
                stopSequence = 29,
                versions = listOf(version)
            )
        )
        assertThat(scheduledStops).contains(
            ScheduledStop(
                tripID = "3281905-PI-A22-PI_GTFS-Semaine-01",
                stopID = "72819",
                departsAt = DateTimeOffset(hours = 6, minutes = 53, seconds = 48),
                arrivesAt = DateTimeOffset(hours = 6, minutes = 53, seconds = 48),
                stopSequence = 30,
                versions = listOf(version)
            )
        )
        assertThat(scheduledStops).contains(
            ScheduledStop(
                tripID = "3281905-PI-A22-PI_GTFS-Semaine-01",
                stopID = "72728",
                departsAt = DateTimeOffset(hours = 6, minutes = 54, seconds = 57),
                arrivesAt = DateTimeOffset(hours = 6, minutes = 54, seconds = 57),
                stopSequence = 31,
                versions = listOf(version)
            )
        )
        assertThat(scheduledStops).contains(
            ScheduledStop(
                tripID = "3281905-PI-A22-PI_GTFS-Semaine-01",
                stopID = "72825",
                departsAt = DateTimeOffset(hours = 6, minutes = 55, seconds = 23),
                arrivesAt = DateTimeOffset(hours = 6, minutes = 55, seconds = 23),
                stopSequence = 32,
                versions = listOf(version)
            )
        )
        assertThat(scheduledStops).contains(
            ScheduledStop(
                tripID = "3281905-PI-A22-PI_GTFS-Semaine-01",
                stopID = "72968",
                departsAt = DateTimeOffset(hours = 6, minutes = 56, seconds = 7),
                arrivesAt = DateTimeOffset(hours = 6, minutes = 56, seconds = 7),
                stopSequence = 33,
                versions = listOf(version)
            )
        )
        assertThat(scheduledStops).contains(
            ScheduledStop(
                tripID = "3281905-PI-A22-PI_GTFS-Semaine-01",
                stopID = "72914",
                departsAt = DateTimeOffset(hours = 6, minutes = 57, seconds = 33),
                arrivesAt = DateTimeOffset(hours = 6, minutes = 57, seconds = 33),
                stopSequence = 34,
                versions = listOf(version)
            )
        )
        assertThat(scheduledStops).contains(
            ScheduledStop(
                tripID = "3281905-PI-A22-PI_GTFS-Semaine-01",
                stopID = "72617",
                departsAt = DateTimeOffset(hours = 6, minutes = 58, seconds = 0),
                arrivesAt = DateTimeOffset(hours = 6, minutes = 58, seconds = 0),
                stopSequence = 35,
                versions = listOf(version)
            )
        )
        assertThat(scheduledStops).contains(
            ScheduledStop(
                tripID = "3281905-PI-A22-PI_GTFS-Semaine-01",
                stopID = "72712",
                departsAt = DateTimeOffset(hours = 6, minutes = 58, seconds = 59),
                arrivesAt = DateTimeOffset(hours = 6, minutes = 58, seconds = 59),
                stopSequence = 36,
                versions = listOf(version)
            )
        )
        assertThat(scheduledStops).contains(
            ScheduledStop(
                tripID = "3281905-PI-A22-PI_GTFS-Semaine-01",
                stopID = "72961",
                departsAt = DateTimeOffset(hours = 7, minutes = 0, seconds = 19),
                arrivesAt = DateTimeOffset(hours = 7, minutes = 0, seconds = 19),
                stopSequence = 37,
                versions = listOf(version)
            )
        )
        assertThat(scheduledStops).contains(
            ScheduledStop(
                tripID = "3281905-PI-A22-PI_GTFS-Semaine-01",
                stopID = "72750",
                departsAt = DateTimeOffset(hours = 7, minutes = 1, seconds = 3),
                arrivesAt = DateTimeOffset(hours = 7, minutes = 1, seconds = 3),
                stopSequence = 38,
                versions = listOf(version)
            )
        )
        assertThat(scheduledStops).contains(
            ScheduledStop(
                tripID = "3281905-PI-A22-PI_GTFS-Semaine-01",
                stopID = "72986",
                departsAt = DateTimeOffset(hours = 7, minutes = 3, seconds = 0),
                arrivesAt = DateTimeOffset(hours = 7, minutes = 3, seconds = 0),
                stopSequence = 39,
                versions = listOf(version)
            )
        )
        assertThat(scheduledStops).hasSize(9262)
    }

    @AfterEach
    fun clear() {
        regionRepository.deleteAll()
        scheduledStopRepository.deleteAll()
        scheduledTripRepository.deleteAll()
        stopRepository.deleteAll()
        routeRepository.deleteAll()
        agencyRepository.deleteAll()
        feedVersionRepository.deleteAll()
        feedRepository.deleteAll()
    }

}
