package com.mobilispect.backend.feed.batch.import

import com.conveyal.gtfs.GTFSFeed
import com.mobilispect.backend.feed.domain.model.ids.FeedId
import com.mobilispect.backend.feed.events.FeedImportFailed
import com.mobilispect.backend.feed.events.FeedImportStepCompleted
import com.mobilispect.backend.feed.events.FeedImportStepStartedEvent
import com.mobilispect.backend.feed.api.GTFSAgency
import com.mobilispect.backend.feed.api.GTFSData
import com.mobilispect.backend.feed.api.GTFSRoute
import com.mobilispect.backend.feed.api.GTFSShapePoint
import com.mobilispect.backend.feed.api.GTFSStop
import com.mobilispect.backend.feed.api.GTFSStopTime
import com.mobilispect.backend.feed.api.GTFSTrip
import com.mobilispect.backend.feed.api.ids.GTFSAgencyId
import com.mobilispect.backend.feed.api.ids.GTFSRouteId
import com.mobilispect.backend.feed.api.ids.GTFSStopId
import com.mobilispect.backend.feed.api.ids.GTFSTripId
import com.mobilispect.backend.feed.model.FeedEntity
import com.mobilispect.backend.feed.repository.FeedRepository
import com.mobilispect.backend.schedule.download.DownloadRequest
import com.mobilispect.backend.schedule.download.Downloader
import com.mobilispect.backend.util.ArchiveExtractor
import org.slf4j.LoggerFactory
import org.springframework.batch.core.configuration.annotation.StepScope
import org.springframework.batch.infrastructure.item.ItemReader
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Service
import java.nio.file.Files
import java.nio.file.Path
import java.time.LocalTime

/**
 *  GTFS feed import reader.
 *
 * This service handles GTFS feed imports for the feed management system,
 * downloading and processing feeds.
 */
@Service
@StepScope
class GTFSFeedReader(
    @Qualifier("feedManagementFeedRepository") private val feedRepository: FeedRepository,
    @Qualifier("curlDownloader") private val downloader: Downloader,
    private val archiveExtractor: ArchiveExtractor,
    private val eventPublisher: ApplicationEventPublisher,
) : ItemReader<GTFSData> {
    private val logger = LoggerFactory.getLogger(GTFSFeedReader::class.java)


    @Value("#{jobParameters['feedOnestopId']}")
    lateinit var feedOnestopId: String

    private var hasRun = false

    /**
     * Import a feed by its onestop ID.
     *
     * Downloads the GTFS feed, extracts it, and processes the data.
     *
     * @param feedId The onestop ID of the feed to import
     * @return Result containing the version SHA1 hash of the imported feed on success
     */
    fun importFeedById(feedId: FeedId): Result<GTFSData> {
        logger.info("Starting PostgreSQL-based import for feed: {}", feedId)

        val feed = feedRepository.findByFeedOnestopId(feedId.value).orElse(null)
            ?: return Result.failure(IllegalArgumentException("Feed not found: $feedId"))

        if (feed.downloadUrl.isBlank()) {
            return Result.failure(IllegalArgumentException("Feed $feedId has no download URL"))
        }

        val archive = doStep(feedId, "download") {
            downloadFeed(feed)
        }.getOrNull() ?: return Result.failure(IllegalStateException("Download failed"))

        val extractedDir = doStep(feedId, "extract") {
            extractFeed(archive)
        }.getOrNull() ?: return Result.failure(IllegalStateException("Extraction failed"))

        doStep(feedId, "validate") {
            validateGtfsFiles(extractedDir)
        }.getOrNull() ?: return Result.failure(IllegalStateException("Validation failed"))

        return doStep(feedId, "parse") { parse(archive) }
    }

    override fun read(): GTFSData? {
        if (hasRun) return null
        hasRun = true

        val feedId = FeedId.Companion.from(feedOnestopId)
            ?: throw IllegalArgumentException("feedOnestopId job parameter is required")

        return importFeedById(feedId).getOrElse { throw it }
    }

    private fun <T> doStep(feedId: FeedId, step: String, function: () -> Result<T>): Result<T> {
        eventPublisher.publishEvent(FeedImportStepStartedEvent(feedId, step))
        val res = function()
        if (res.isFailure) {
            eventPublisher.publishEvent(
                FeedImportFailed(
                    feedId,
                    step,
                    res.exceptionOrNull()?.message ?: "Unknown error"
                )
            )
            return Result.failure(res.exceptionOrNull()!!)
        }
        eventPublisher.publishEvent(FeedImportStepCompleted(feedId, step))
        return res
    }

    private fun downloadFeed(feed: FeedEntity): Result<Path> {
        return runCatching {
            logger.info("Downloading feed from: {}", feed.downloadUrl)

            val request = DownloadRequest(url = feed.downloadUrl)
            downloader.download(request).getOrThrow()
        }
    }

    private fun extractFeed(archive: Path): Result<Path> {
        return runCatching {
            logger.info("Extracting archive: {}", archive)
            archiveExtractor.extract(archive).getOrThrow()
        }
    }

    private fun validateGtfsFiles(extractedDir: Path): Result<Path> {
        return runCatching {
            logger.info("Validating GTFS files in: {}", extractedDir)

            // Check for required GTFS files
            val requiredFiles = listOf("agency.txt", "stops.txt", "routes.txt", "trips.txt", "stop_times.txt")
            val missingFiles = requiredFiles.filter { !Files.exists(extractedDir.resolve(it)) }

            if (missingFiles.isNotEmpty()) {
                throw IllegalStateException("Missing required GTFS files: ${missingFiles.joinToString(", ")}")
            }

            logger.info("GTFS validation successful")
            return Result.success(extractedDir)
        }
    }

    /**
     * Parse a GTFS feed archive.
     *
     * Uses the Conveyal gtfs-lib (6.2.0), which is the successor to OneBusAway.
     * Provides better performance for large feeds with disk-backed storage via MapDB.
     *
     * Key improvements over OneBusAway:
     * - Handles feeds larger than available memory
     * - Better error tolerance and validation
     * - Modern Java/Kotlin compatibility
     * - Active maintenance
     *
     * @param feedPath Path to the GTFS feed ZIP file
     * @return Result containing parsed data on success, or error on failure
     */
    fun parse(feedPath: Path): Result<GTFSData> = runCatching {
        logger.info("Parsing GTFS feed at: {}", feedPath)
        val feed = GTFSFeed.fromFile(feedPath.toString())
        feed.use { feed ->
            val agencies = feed.agency.values.map { agency ->
                GTFSAgency(
                    agencyId = GTFSAgencyId(agency.agency_id),
                    name = agency.agency_name,
                    url = agency.agency_url?.toString(),
                    timezone = agency.agency_timezone,
                    phone = agency.agency_phone
                )
            }

            val routes = feed.routes.values.map { route ->
                GTFSRoute(
                    routeId = GTFSRouteId(route.route_id),
                    agencyId = GTFSAgencyId.from(route.agency_id),
                    shortName = route.route_short_name,
                    longName = route.route_long_name,
                    type = route.route_type
                )
            }

            val stops = feed.stops.values.map { stop ->
                GTFSStop(
                    stopId = GTFSStopId(stop.stop_id),
                    name = stop.stop_name,
                    latitude = stop.stop_lat,
                    longitude = stop.stop_lon,
                    stopCode = stop.stop_code,
                    stopDesc = stop.stop_desc,
                    zoneId = stop.zone_id,
                    stopUrl = stop.stop_url?.toString(),
                    locationType = stop.location_type,
                    parentStation = stop.parent_station
                )
            }

            val shapes = feed.shape_points.values.groupBy { it.shape_id }.mapValues { (_, points) ->
                    points.sortedBy { it.shape_pt_sequence }.map { point ->
                        GTFSShapePoint(
                            latitude = point.shape_pt_lat,
                            longitude = point.shape_pt_lon,
                            sequence = point.shape_pt_sequence,
                            distTraveledKm = point.shape_dist_traveled
                        )
                        }
                }

            val trips = feed.trips.values.map { trip ->
                val stopTimes = feed.getOrderedStopTimesForTrip(trip.trip_id).map { stopTime ->
                    GTFSStopTime(
                        stopId = GTFSStopId(stopTime.stop_id),
                        stopSequence = stopTime.stop_sequence,
                        departureTime = stopTime.departure_time.takeIf { it >= 0 }?.let { seconds ->
                            // GTFS allows times >= 24:00:00 for overnight service
                            // Normalize to 0-86399 range for LocalTime
                            LocalTime.ofSecondOfDay((seconds % 86400).toLong())
                        },
                        shapeDistTraveledKm = stopTime.shape_dist_traveled
                    )
                    }

                GTFSTrip(
                    routeId = GTFSRouteId(trip.route_id),
                    tripId = GTFSTripId(trip.trip_id),
                    directionId = trip.direction_id,
                    headsign = trip.trip_headsign,
                    shapeId = trip.shape_id,
                    stopTimes = stopTimes
                )
            }

            logger.info(
                "Parsed GTFS feed at {} -> {} agencies, {} routes, {} trips",
                feedPath,
                agencies.size,
                routes.size,
                trips.size
            )
            GTFSData(
                agencies = agencies, routes = routes, trips = trips, stops = stops, shapes = shapes
            )
        }
    }
}
