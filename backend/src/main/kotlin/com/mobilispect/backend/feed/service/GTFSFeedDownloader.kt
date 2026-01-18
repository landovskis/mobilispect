package com.mobilispect.backend.feed.service

import com.conveyal.gtfs.GTFSFeed
import com.mobilispect.backend.feed.api.GTFSAgency
import com.mobilispect.backend.feed.api.GTFSData
import com.mobilispect.backend.feed.api.GTFSRoute
import com.mobilispect.backend.feed.api.GTFSShapePoint
import com.mobilispect.backend.feed.api.GTFSStop
import com.mobilispect.backend.feed.api.GTFSStopTime
import com.mobilispect.backend.feed.api.GTFSTrip
import com.mobilispect.backend.feed.api.ids.FeedLocalAgencyId
import com.mobilispect.backend.feed.api.ids.FeedLocalRouteId
import com.mobilispect.backend.feed.api.ids.GTFSStopId
import com.mobilispect.backend.feed.api.ids.GTFSTripId
import com.mobilispect.backend.feed.domain.model.ids.FeedId
import com.mobilispect.backend.feed.model.FeedEntity
import com.mobilispect.backend.feed.repository.FeedRepository
import com.mobilispect.backend.schedule.download.DownloadRequest
import com.mobilispect.backend.schedule.download.Downloader
import com.mobilispect.backend.util.ArchiveExtractor
import java.nio.file.Files
import java.nio.file.Path
import java.time.LocalTime
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Service

/**
 * Service for downloading and parsing GTFS feeds.
 *
 * This service extracts the core GTFS download/parse logic that was previously in GTFSFeedReader,
 * making it available for both batch processing and synchronous parallel execution.
 *
 * Unlike the batch-scoped GTFSFeedReader, this service:
 * - Is a singleton (not step-scoped)
 * - Doesn't depend on batch job parameters
 * - Is thread-safe for parallel execution
 *
 * Constitutional Requirements:
 * - Module boundaries: Uses feed repository for feed metadata
 * - Observability: Structured logging for download/parse steps
 */
@Service
class GTFSFeedDownloader(
  @Qualifier("feedManagementFeedRepository") private val feedRepository: FeedRepository,
  @Qualifier("curlDownloader") private val downloader: Downloader,
  private val archiveExtractor: ArchiveExtractor,
) {
  private val logger = LoggerFactory.getLogger(GTFSFeedDownloader::class.java)

  /**
   * Download and parse a feed by its onestop ID.
   *
   * @param feedId The onestop ID of the feed to import
   * @return Result containing the parsed GTFSData on success
   */
  fun downloadAndParse(feedId: FeedId): Result<GTFSData> {
    logger.info("Starting download and parse for feed: {}", feedId)

    val feed =
      feedRepository.findByFeedOnestopId(feedId.value).orElse(null)
        ?: return Result.failure(IllegalArgumentException("Feed not found: $feedId"))

    if (feed.downloadUrl.isBlank()) {
      return Result.failure(IllegalArgumentException("Feed $feedId has no download URL"))
    }

    val archive =
      downloadFeed(feed).getOrNull()
        ?: return Result.failure(IllegalStateException("Download failed for feed $feedId"))

    val extractedDir =
      extractFeed(archive).getOrNull()
        ?: return Result.failure(IllegalStateException("Extraction failed for feed $feedId"))

    validateGtfsFiles(extractedDir).getOrNull()
      ?: return Result.failure(IllegalStateException("Validation failed for feed $feedId"))

    return parse(archive, feedId)
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

      val requiredFiles =
        listOf("agency.txt", "stops.txt", "routes.txt", "trips.txt", "stop_times.txt")
      val missingFiles = requiredFiles.filter { !Files.exists(extractedDir.resolve(it)) }

      if (missingFiles.isNotEmpty()) {
        throw IllegalStateException(
          "Missing required GTFS files: ${missingFiles.joinToString(", ")}"
        )
      }

      logger.info("GTFS validation successful")
      extractedDir
    }
  }

  /**
   * Parse a GTFS feed archive.
   *
   * Uses the Conveyal gtfs-lib (6.2.0), which is the successor to OneBusAway.
   */
  fun parse(feedPath: Path, feedId: FeedId): Result<GTFSData> = runCatching {
    logger.info("Started parsing GTFS feed {}", feedId)
    val feed = GTFSFeed.fromFile(feedPath.toString(), feedId.value)
    feed.use { gtfsFeed ->
      val agencies =
        gtfsFeed.agency.values.map { agency ->
          GTFSAgency(
            agencyId = FeedLocalAgencyId(agency.agency_id),
            name = agency.agency_name,
            url = agency.agency_url?.toString(),
            timezone = agency.agency_timezone,
            phone = agency.agency_phone,
          )
        }

      val routes =
        gtfsFeed.routes.values.map { route ->
          GTFSRoute(
            routeId = FeedLocalRouteId(route.route_id),
            agencyId = FeedLocalAgencyId.from(route.agency_id),
            shortName = route.route_short_name,
            longName = route.route_long_name,
            type = route.route_type,
          )
        }

      val stops =
        gtfsFeed.stops.values.map { stop ->
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
            parentStation = stop.parent_station,
          )
        }

      val shapes =
        gtfsFeed.shape_points.values
          .groupBy { it.shape_id }
          .mapValues { (_, points) ->
            points
              .sortedBy { it.shape_pt_sequence }
              .map { point ->
                GTFSShapePoint(
                  latitude = point.shape_pt_lat,
                  longitude = point.shape_pt_lon,
                  sequence = point.shape_pt_sequence,
                  distTraveledKm = point.shape_dist_traveled,
                )
              }
          }

      val trips =
        gtfsFeed.trips.values.map { trip ->
          val stopTimes =
            gtfsFeed.getOrderedStopTimesForTrip(trip.trip_id).map { stopTime ->
              GTFSStopTime(
                stopId = GTFSStopId(stopTime.stop_id),
                stopSequence = stopTime.stop_sequence,
                departureTime =
                  stopTime.departure_time
                    .takeIf { it >= 0 }
                    ?.let { seconds ->
                      // GTFS allows times >= 24:00:00 for overnight service
                      // Normalize to 0-86399 range for LocalTime
                      LocalTime.ofSecondOfDay((seconds % 86400).toLong())
                    },
                shapeDistTraveledKm = stopTime.shape_dist_traveled,
              )
            }

          GTFSTrip(
            routeId = FeedLocalRouteId(trip.route_id),
            tripId = GTFSTripId(trip.trip_id),
            directionId = trip.direction_id,
            headsign = trip.trip_headsign,
            shapeId = trip.shape_id,
            stopTimes = stopTimes,
          )
        }

      logger.info(
        "Parsed GTFS feed at {} -> {} agencies, {} routes, {} trips",
        feedPath,
        agencies.size,
        routes.size,
        trips.size,
      )
      logger.info("Finished parsing GTFS feed {}", feedId)
      GTFSData(agencies = agencies, routes = routes, trips = trips, stops = stops, shapes = shapes)
    }
  }
}
