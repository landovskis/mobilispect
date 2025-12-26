package com.mobilispect.backend.transitanalysis.infrastructure.gtfs

import com.conveyal.gtfs.GTFSFeed
import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Primary
import org.springframework.stereotype.Component
import java.nio.file.Path
import java.time.LocalTime

/**
 * Conveyal GTFS library-based parser.
 *
 * Uses the modern Conveyal gtfs-lib (6.2.0), which is the successor to OneBusAway.
 * Provides better performance for large feeds with disk-backed storage via MapDB.
 *
 * Key improvements over OneBusAway:
 * - Handles feeds larger than available memory
 * - Better error tolerance and validation
 * - Modern Java/Kotlin compatibility
 * - Active maintenance
 */
@Component
@Primary
class ConveyalGtfsParser : GtfsParser {
    private val logger = LoggerFactory.getLogger(ConveyalGtfsParser::class.java)

    override fun parse(feedPath: Path): Result<ParsedGtfsData> = runCatching {
        logger.info("Parsing GTFS feed at: {}", feedPath)

        val feed = GTFSFeed.fromFile(feedPath.toString())

        try {
            val agencies = feed.agency.values.map { agency ->
                ParsedAgency(
                    agencyId = agency.agency_id,
                    name = agency.agency_name,
                    url = agency.agency_url?.toString(),
                    timezone = agency.agency_timezone,
                    phone = agency.agency_phone
                )
            }

            val routes = feed.routes.values.map { route ->
                ParsedRoute(
                    routeId = route.route_id,
                    agencyId = route.agency_id,
                    shortName = route.route_short_name,
                    longName = route.route_long_name,
                    type = route.route_type
                )
            }

            val stops = feed.stops.values.map { stop ->
                ParsedStop(
                    stopId = stop.stop_id,
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

            val shapes = feed.shape_points.values
                .groupBy { it.shape_id }
                .mapValues { (_, points) ->
                    points
                        .sortedBy { it.shape_pt_sequence }
                        .map { point ->
                            ParsedShapePoint(
                                latitude = point.shape_pt_lat,
                                longitude = point.shape_pt_lon,
                                sequence = point.shape_pt_sequence,
                                distTraveledKm = point.shape_dist_traveled
                            )
                        }
                }

            val trips = feed.trips.values.map { trip ->
                val stopTimes = feed.getOrderedStopTimesForTrip(trip.trip_id)
                    .map { stopTime ->
                        ParsedStopTime(
                            stopId = stopTime.stop_id,
                            stopSequence = stopTime.stop_sequence,
                            departureTime = stopTime.departure_time.takeIf { it >= 0 }?.let { seconds ->
                                // GTFS allows times >= 24:00:00 for overnight service
                                // Normalize to 0-86399 range for LocalTime
                                LocalTime.ofSecondOfDay((seconds % 86400).toLong())
                            },
                            shapeDistTraveledKm = stopTime.shape_dist_traveled
                        )
                    }

                ParsedTrip(
                    routeId = trip.route_id,
                    tripId = trip.trip_id,
                    directionId = trip.direction_id,
                    headsign = trip.trip_headsign,
                    shapeId = trip.shape_id,
                    stopTimes = stopTimes
                )
            }

            logger.info("Parsed GTFS feed at {} -> {} agencies, {} routes, {} trips", feedPath, agencies.size, routes.size, trips.size)
            ParsedGtfsData(
                agencies = agencies,
                routes = routes,
                trips = trips,
                stops = stops,
                shapes = shapes
            )
        } finally {
            // Clean up MapDB resources
            feed.close()
        }
    }
}
