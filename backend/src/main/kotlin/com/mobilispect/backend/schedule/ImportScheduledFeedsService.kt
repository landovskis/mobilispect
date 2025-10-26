package com.mobilispect.backend.schedule

import arrow.core.Ior
import com.mobilispect.backend.Agency
import com.mobilispect.backend.AgencyDataSource
import com.mobilispect.backend.AgencyRepository
import com.mobilispect.backend.FeedDataSource
import com.mobilispect.backend.FeedRepository
import com.mobilispect.backend.FeedVersionRepository
import com.mobilispect.backend.RegionRepository
import com.mobilispect.backend.infastructure.Stop
import com.mobilispect.backend.infastructure.StopRepository
import com.mobilispect.backend.schedule.download.DownloadRequest
import com.mobilispect.backend.schedule.download.Downloader
import com.mobilispect.backend.schedule.route.RouteDataSource
import com.mobilispect.backend.schedule.stop.StopDataSource
import com.mobilispect.backend.util.ArchiveExtractor
import io.github.resilience4j.retry.annotation.Retry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.data.repository.CrudRepository
import org.springframework.stereotype.Service
import java.nio.file.Path
import java.time.Clock
import java.time.Duration
import java.time.Instant

/**
 * Import all scheduled listed in [feedDataSource].
 */
@Service
@Suppress("LongParameterList")
class ImportScheduledFeedsService(
    private val feedDataSource: FeedDataSource,
    private val feedRepository: FeedRepository,
    private val feedVersionRepository: FeedVersionRepository,
    private val downloader: Downloader,
    private val archiveExtractor: ArchiveExtractor,
    private val regionRepository: RegionRepository,
    private val agencyRepository: AgencyRepository,
    private val routeRepository: RouteRepository,
    private val stopRepository: StopRepository,
    private val scheduledTripRepository: ScheduledTripRepository,
    private val scheduledStopRepository: ScheduledStopRepository,
    private val agencyDataSource: AgencyDataSource,
    private val routeDataSource: RouteDataSource,
    private val stopDataSource: StopDataSource,
    private val scheduledTripDataSource: ScheduledTripDataSource,
    private val scheduledStopDataSource: ScheduledStopDataSource,
    private val clock: Clock = Clock.systemDefaultZone()
) {
    private val logger: Logger = LoggerFactory.getLogger(ImportScheduledFeedsService::class.java)

    suspend operator fun invoke(): Boolean {
        return withContext(Dispatchers.IO) {
            logger.info("Started")
            var updatedFeeds = findUpdatedFeeds()
            if (updatedFeeds.isEmpty()) {
                logger.info("Completed without updates")
                return@withContext true
            }
            updatedFeeds = updatedFeeds.filter { it.feed.uid == "f-f25d-socitdetransportdemontral" }

            val results = updatedFeeds.map { updatedFeed -> importFeed(updatedFeed) }

            if (results.all { result -> result.isSuccess }) {
                logger.info("Completed with updates")
                return@withContext true
            } else {
                logger.error("Completed with errors")
                return@withContext false
            }
        }
    }

    private fun findUpdatedFeeds(): List<ScheduledFeed> {
        val regions = regionRepository.findAll()
        val feeds = regions.flatMap { region -> feedDataSource.feeds(region.name) }
            .filter { it.isSuccess }
            .mapNotNull { it.getOrNull() }
        logger.debug("Found feeds: {}", feeds)
        return feeds
    }

    @Suppress("ReturnCount")
    private fun importFeed(cloudFeed: ScheduledFeed): Result<*> {
        logger.debug("Import started: {} version {}", cloudFeed.feed.url, cloudFeed.version.uid)
        return downloadFeed(cloudFeed)
            .map { archive -> extractFeed(archive) }
            .map { extractedDirRes ->
                extractedDirRes.getOrNull()?.let { extractedDir ->
                    val agencyRes = importAgencies(
                        version = cloudFeed.version.uid,
                        extractedDir = extractedDir,
                        feedID = cloudFeed.feed.uid
                    )
                    if (agencyRes.isFailure) {
                        return agencyRes
                    }

                    val routeRes = importRoutes(
                        version = cloudFeed.version.uid,
                        extractedDir = extractedDir,
                        feedID = cloudFeed.feed.uid
                    )
                    if (routeRes.isFailure) {
                        return routeRes
                    }

                    importStops(
                        version = cloudFeed.version.uid,
                        extractedDir = extractedDir,
                        feedID = cloudFeed.feed.uid
                    )

                    val tripRes = importTrips(
                        version = cloudFeed.version.uid,
                        extractedDir = extractedDir,
                        feedID = cloudFeed.feed.uid
                    )
                    if (tripRes.isFailure) {
                        return tripRes
                    }

                    val stopTimeRes = importStopTimes(cloudFeed.version.uid, extractedDir)
                    if (stopTimeRes.isFailure) {
                        return stopTimeRes
                    }

                    save(cloudFeed.feed, feedRepository)
                    save(cloudFeed.version, feedVersionRepository)
                }
            }
            .onSuccess { feed -> logger.debug("Import completed: {}", feed) }
    }

    private fun downloadFeed(cloudFeed: ScheduledFeed): Result<Path> {
        val start = Instant.now(clock)
        return downloader.download(DownloadRequest(url = cloudFeed.feed.url))
            .onSuccess { _ ->
                val elapsed = Duration.between(start, Instant.now(clock))
                logger.debug("Downloaded feed from {} in {}", cloudFeed.feed.url, elapsed)
            }
            .onFailure { exception -> logger.error("Error downloading feed from ${cloudFeed.feed.url}: $exception") }
    }

    private fun extractFeed(archive: Path): Result<Path> {
        val start = clock.instant()
        return archiveExtractor.extract(archive)
            .onSuccess { path ->
                val elapsed = Duration.between(start, clock.instant())
                logger.debug("Extracted archive to {} in {}", path, elapsed)
            }
            .onFailure { exception -> logger.error("Error extracting feed: $exception") }
    }

    private fun importAgencies(version: String, extractedDir: Path, feedID: String): Result<Collection<Agency>> {
        val start = clock.instant()
        return agencyDataSource.agencies(root = extractedDir, version = version, feedID = feedID)
            .map { agencies -> agencies.map { save(it, agencyRepository) } }
            .onSuccess { agencies ->
                val elapsed = Duration.between(start, clock.instant())
                logger.debug("Imported {} agencies in {}", agencies.size, elapsed)
            }
            .onFailure { e -> logger.error("Failed to import agencies: $e") }
    }

    private fun importRoutes(version: String, extractedDir: Path, feedID: String): Result<Collection<Route>> {
        val start = clock.instant()
        return routeDataSource.routes(root = extractedDir, version = version, feedID = feedID)
            .map { routes -> routes.map { save(it, routeRepository) } }
            .onSuccess { routes ->
                val elapsed = Duration.between(start, clock.instant())
                logger.debug("Imported {} routes in {}", routes.size, elapsed)
            }
            .onFailure { e -> logger.error("Failed to import routes: $e") }
    }

    private fun importStops(
        version: String,
        extractedDir: Path,
        feedID: String
    ): Ior<Collection<Throwable>, Collection<Stop>> {
        val start = clock.instant()
        stopDataSource.stops(extractedDir, version, feedID)
            .map { stops -> stops.map { save(it, stopRepository) } }
            .fold(
                { errors ->
                    logger.error("Failed to import stops: {}", errors)
                    return Ior.Left(errors)
                },
                { stops ->
                    val elapsed = Duration.between(start, clock.instant())
                    logger.debug("Imported {} stops in {}", stops.size, elapsed)
                    return Ior.Right(stops)
                },
                { errors, stops ->
                    val elapsed = Duration.between(start, clock.instant())
                    logger.error(
                        "Partially imported {}/{} stops in {}: {}",
                        stops.size,
                        stops.size + errors.size,
                        elapsed,
                        errors
                    )
                    return Ior.Both(errors, stops)
                })

    }

    private fun importTrips(version: String, extractedDir: Path, feedID: String): Result<Collection<ScheduledTrip>> {
        val start = clock.instant()
        return scheduledTripDataSource.trips(extractedDir, version, feedID)
            .map { trips -> trips.map { save(it, scheduledTripRepository) } }
            .onSuccess { trips ->
                val elapsed = Duration.between(start, clock.instant())
                logger.debug("Imported {} trips in {}", trips.size, elapsed)
            }
            .onFailure { e -> logger.error("Failed to import scheduled trips: $e") }
    }

    private fun importStopTimes(version: String, extractedDir: Path): Result<Collection<ScheduledStop>> {
        val start = clock.instant()
        return scheduledStopDataSource.scheduledStops(extractedDir, version)
            .map { scheduledStops -> scheduledStops.map { save(it, scheduledStopRepository) } }
            .onSuccess { stops ->
                val elapsed = Duration.between(start, clock.instant())
                logger.debug("Imported {} stop times in {}", stops.size, elapsed)
            }
            .onFailure { e -> logger.error("Failed to import stop times: $e") }
    }

    @Retry(name = "save")
    private fun <T : Any> save(element: T, repository: CrudRepository<T, String>): T {
        return repository.save(element)
    }
}
