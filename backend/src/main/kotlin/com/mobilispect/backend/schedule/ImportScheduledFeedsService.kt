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
import com.mobilispect.backend.websocket.ProgressTrackingService
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
    private val progressTrackingService: ProgressTrackingService,
    private val clock: Clock = Clock.systemDefaultZone()
) {
    private val logger: Logger = LoggerFactory.getLogger(ImportScheduledFeedsService::class.java)

    suspend operator fun invoke(): Boolean {
        return withContext(Dispatchers.IO) {
            logger.info("Started")
            val updatedFeeds = findUpdatedFeeds()
            if (updatedFeeds.isEmpty()) {
                logger.info("Completed without updates")
                return@withContext true
            }

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

    /**
     * Import a specific feed by its onestop ID
     */
    suspend fun importFeedById(feedOnestopId: String): Result<String> {
        return withContext(Dispatchers.IO) {
            logger.info("Starting import for feed: $feedOnestopId")

            // Find the feed
            val regions = regionRepository.findAll()
            val allFeeds = regions.flatMap { region -> feedDataSource.feeds(region.name) }
                .filter { it.isSuccess }
                .mapNotNull { it.getOrNull() }

            val scheduledFeed = allFeeds.firstOrNull { it.feed.uid == feedOnestopId }
                ?: return@withContext Result.failure(
                    IllegalArgumentException("Feed not found: $feedOnestopId")
                )

            val importId = "${scheduledFeed.feed.uid}:${scheduledFeed.version.uid}"

            // Import the feed
            importFeed(scheduledFeed)
                .map { importId }
                .onSuccess { logger.info("Successfully started import for feed: $feedOnestopId") }
                .onFailure { error -> logger.error("Failed to start import for feed: $feedOnestopId", error) }
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

    @Suppress("TooGenericExceptionCaught")
    private fun importFeed(cloudFeed: ScheduledFeed): Result<Unit> {
        logger.debug("Import started: {} version {}", cloudFeed.feed.url, cloudFeed.version.uid)

        val importId = "${cloudFeed.feed.uid}:${cloudFeed.version.uid}"
        val startedAt = clock.instant()
        val totalSteps = 8

        fun progress(stepNumber: Int, stepName: String, percentage: Int = (stepNumber * 100) / totalSteps) {
            progressTrackingService.updateProgress(
                importId = importId,
                feedOnestopId = cloudFeed.feed.uid,
                progressPercentage = percentage.coerceAtMost(100),
                currentStep = stepName,
                currentStepNumber = stepNumber,
                totalSteps = totalSteps,
                startedAt = startedAt
            )
        }

        progress(stepNumber = 0, stepName = "Preparing import", percentage = 0)

        val result = runCatching {
            progress(stepNumber = 1, stepName = "Downloading feed")
            val archive = downloadFeed(cloudFeed).getOrThrow()

            progress(stepNumber = 2, stepName = "Extracting feed")
            val extractedDir = extractFeed(archive).getOrThrow()

            progress(stepNumber = 3, stepName = "Importing agencies")
            importAgencies(
                version = cloudFeed.version.uid,
                extractedDir = extractedDir,
                feedID = cloudFeed.feed.uid
            ).getOrThrow()

            progress(stepNumber = 4, stepName = "Importing routes")
            importRoutes(
                version = cloudFeed.version.uid,
                extractedDir = extractedDir,
                feedID = cloudFeed.feed.uid
            ).getOrThrow()

            progress(stepNumber = 5, stepName = "Importing stops")
            importStops(
                version = cloudFeed.version.uid,
                extractedDir = extractedDir,
                feedID = cloudFeed.feed.uid
            )

            progress(stepNumber = 6, stepName = "Importing trips")
            importTrips(
                version = cloudFeed.version.uid,
                extractedDir = extractedDir,
                feedID = cloudFeed.feed.uid
            ).getOrThrow()

            progress(stepNumber = 7, stepName = "Importing stop times")
            importStopTimes(cloudFeed.version.uid, extractedDir).getOrThrow()

            progress(stepNumber = 8, stepName = "Finalizing import", percentage = 100)
            save(cloudFeed.feed, feedRepository)
            save(cloudFeed.version, feedVersionRepository)
            Unit
        }

        return result
            .onSuccess {
                progressTrackingService.markCompleted(importId)
                logger.debug("Import completed: {}", cloudFeed.feed.uid)
            }
            .onFailure { exception ->
                progressTrackingService.markFailed(importId, exception.message ?: "Import failed")
                logger.error("Import failed for {}: {}", importId, exception.message, exception)
            }
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
