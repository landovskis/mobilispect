package com.mobilispect.backend.schedule

import arrow.core.Ior
import com.mobilispect.backend.Agency
import com.mobilispect.backend.AgencyDataSource
import com.mobilispect.backend.AgencyRepository
import com.mobilispect.backend.Feed
import com.mobilispect.backend.FeedDataSource
import com.mobilispect.backend.FeedRepository
import com.mobilispect.backend.FeedVersion
import com.mobilispect.backend.FeedVersionRepository
import com.mobilispect.backend.Region
import com.mobilispect.backend.RegionRepository
import com.mobilispect.backend.infastructure.Stop
import com.mobilispect.backend.infastructure.StopRepository
import com.mobilispect.backend.schedule.Route
import com.mobilispect.backend.schedule.RouteRepository
import com.mobilispect.backend.schedule.ScheduledFeed
import com.mobilispect.backend.schedule.ScheduledStop
import com.mobilispect.backend.schedule.ScheduledStopRepository
import com.mobilispect.backend.schedule.ScheduledTrip
import com.mobilispect.backend.schedule.ScheduledTripRepository
import com.mobilispect.backend.schedule.download.DownloadRequest
import com.mobilispect.backend.schedule.download.Downloader
import com.mobilispect.backend.schedule.route.RouteDataSource
import com.mobilispect.backend.schedule.stop.StopDataSource
import com.mobilispect.backend.util.ArchiveExtractor
import com.mobilispect.backend.websocket.ProgressTrackingService
import com.mobilispect.backend.websocket.ProgressUpdate
import kotlinx.coroutines.test.runTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.ArgumentCaptor
import org.mockito.Mockito
import org.mockito.invocation.InvocationOnMock
import org.mockito.stubbing.Answer
import org.springframework.messaging.simp.SimpMessagingTemplate
import java.nio.file.Files
import java.nio.file.Path
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

@Suppress("LargeClass")
class ImportScheduledFeedsProgressTest {

    @Test
    fun `emits websocket progress during successful feed import`() = runTest {
        val messagingTemplate = Mockito.mock(SimpMessagingTemplate::class.java)
        val progressTrackingService = ProgressTrackingService(messagingTemplate)

        val testFeed = Feed(uid = "f-f25d-socitdetransportdemontral", url = "http://example.com/feed.zip")
        val testVersion = FeedVersion(
            uid = "2024-01-01",
            feedID = testFeed.uid,
            startsOn = LocalDate.of(2024, 1, 1),
            endsOn = LocalDate.of(2024, 12, 31)
        )

        val feedDataSource = object : FeedDataSource {
            override fun feeds(region: String): Collection<Result<ScheduledFeed>> =
                listOf(Result.success(ScheduledFeed(feed = testFeed, version = testVersion)))
        }

        val feedRepository = mockRepository<FeedRepository> { invocation ->
            when (invocation.method.name) {
                "save" -> invocation.getArgument<Feed>(0)
                "findAll" -> emptyList<Feed>()
                else -> null
            }
        }

        val feedVersionRepository = mockRepository<FeedVersionRepository> { invocation ->
            when (invocation.method.name) {
                "save" -> invocation.getArgument<FeedVersion>(0)
                else -> null
            }
        }

        val agencyRepository = mockRepository<AgencyRepository> { invocation ->
            when (invocation.method.name) {
                "save" -> invocation.getArgument<Agency>(0)
                "findAll" -> emptyList<Agency>()
                else -> null
            }
        }

        val routeRepository = mockRepository<RouteRepository> { invocation ->
            when (invocation.method.name) {
                "save" -> invocation.getArgument<Route>(0)
                "findAll" -> emptyList<Route>()
                "findAllByAgencyID" -> emptyList<Route>()
                else -> null
            }
        }

        val stopRepository = mockRepository<StopRepository> { invocation ->
            when (invocation.method.name) {
                "save" -> invocation.getArgument<Stop>(0)
                "findAll" -> emptyList<Stop>()
                else -> null
            }
        }

        val scheduledTripRepository = mockRepository<ScheduledTripRepository> { invocation ->
            when (invocation.method.name) {
                "save" -> invocation.getArgument<ScheduledTrip>(0)
                "findAll" -> emptyList<ScheduledTrip>()
                else -> null
            }
        }

        val scheduledStopRepository = mockRepository<ScheduledStopRepository> { invocation ->
            when (invocation.method.name) {
                "save" -> invocation.getArgument<ScheduledStop>(0)
                "findAll" -> emptyList<ScheduledStop>()
                else -> null
            }
        }

        val regionRepository = Mockito.mock(RegionRepository::class.java)
        Mockito.`when`(regionRepository.findAll()).thenReturn(listOf(Region(uid = "reg-test", name = "Test Region")))

        val downloader = object : Downloader {
            override fun download(request: DownloadRequest): Result<Path> {
                val archive = Files.createTempFile("feed", ".zip")
                return Result.success(archive)
            }
        }
        val extractor = object : ArchiveExtractor {
            override fun extract(archive: Path): Result<Path> = Result.success(Files.createTempDirectory("extracted"))
        }

        val agencyDataSource = object : AgencyDataSource {
            override fun agencies(root: Path, version: String, feedID: String): Result<Collection<Agency>> =
                Result.success(
                    listOf(
                        Agency(
                            uid = "o-test-agency",
                            localID = "agency-1",
                            name = "Test Agency",
                            versions = listOf(version)
                        )
                    )
                )
        }
        val routeDataSource = object : RouteDataSource {
            override fun routes(root: Path, version: String, feedID: String): Result<Collection<Route>> =
                Result.success(
                    listOf(
                        Route(
                            uid = "r-test-route",
                            localID = "route-1",
                            shortName = "1",
                            longName = "Route 1",
                            agencyID = "o-test-agency",
                            versions = listOf(version)
                        )
                    )
                )
        }
        val stopDataSource = object : StopDataSource {
            override fun stops(root: Path, version: String, feedID: String): Ior<Collection<Throwable>, Collection<Stop>> =
                Ior.Right(
                    listOf(
                        Stop(
                            uid = "s-test-stop",
                            localID = "stop-1",
                            name = "Stop 1",
                            versions = listOf(version)
                        )
                    )
                )
        }
        val tripDataSource = object : ScheduledTripDataSource {
            override fun trips(extractedDir: Path, version: String, feedID: String): Result<Collection<ScheduledTrip>> =
                Result.success(
                    listOf(
                        ScheduledTrip(
                            uid = "trip-1",
                            routeID = "r-test-route",
                            dates = listOf(LocalDate.of(2024, 1, 1)),
                            direction = "Outbound",
                            versions = listOf(version)
                        )
                    )
                )
        }
        val scheduledStopDataSource = object : ScheduledStopDataSource {
            override fun scheduledStops(extractedDir: Path, version: String): Result<Collection<ScheduledStop>> =
                Result.success(
                    listOf(
                        ScheduledStop(
                            tripID = "trip-1",
                            stopID = "s-test-stop",
                            departsAt = null,
                            arrivesAt = null,
                            stopSequence = 1,
                            versions = listOf(version)
                        )
                    )
                )
        }

        val service = ImportScheduledFeedsService(
            feedDataSource = feedDataSource,
            feedRepository = feedRepository,
            feedVersionRepository = feedVersionRepository,
            downloader = downloader,
            archiveExtractor = extractor,
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
            progressTrackingService = progressTrackingService,
            clock = Clock.fixed(Instant.parse("2024-01-01T00:00:00Z"), ZoneOffset.UTC)
        )

        val importResult = service()
        assertThat(importResult).isTrue()

        val importId = "${testFeed.uid}:${testVersion.uid}"
        val payloadCaptor = ArgumentCaptor.forClass(ProgressUpdate::class.java)

        Mockito.verify(
            messagingTemplate,
            Mockito.atLeastOnce()
        ).convertAndSend(Mockito.eq("/topic/import/progress/$importId"), payloadCaptor.capture())

        assertThat(payloadCaptor.allValues.any { it.progress != null }).isTrue()
        assertThat(payloadCaptor.allValues.last().completed).isTrue()
    }

    private inline fun <reified R : Any> mockRepository(noinline handler: (InvocationOnMock) -> Any?): R {
        return Mockito.mock(R::class.java, Answer { invocation -> handler(invocation) })
    }
}
