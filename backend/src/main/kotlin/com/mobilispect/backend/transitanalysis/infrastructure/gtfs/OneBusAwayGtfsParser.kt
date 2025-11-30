package com.mobilispect.backend.transitanalysis.infrastructure.gtfs

import org.onebusaway.gtfs.impl.GtfsReader
import org.onebusaway.gtfs.model.StopTime
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.nio.file.Path
import java.time.LocalTime

/**
 * OneBusAway-backed GTFS parser.
 *
 * Keeps the output lean by mapping only the fields needed by the transit-analysis
 * services (routes, trips, stop times).
 */
@Component
class OneBusAwayGtfsParser : GtfsParser {
    private val logger = LoggerFactory.getLogger(OneBusAwayGtfsParser::class.java)

    override fun parse(feedPath: Path): Result<ParsedGtfsData> = runCatching {
        val reader = GtfsReader()
        reader.inputLocation = feedPath.toFile()
        val dao = reader.run()

        val routes = dao.allRoutes.map { route ->
            ParsedRoute(
                routeId = route.id.id,
                agencyId = route.agency?.id?.id,
                shortName = route.shortName,
                longName = route.longName,
                type = route.type
            )
        }

        val trips = dao.allTrips.map { trip ->
            val stopTimes = dao.getStopTimesForTrip(trip)
                .sortedBy(StopTime::getStopSequence)
                .map { stopTime ->
                    ParsedStopTime(
                        stopId = stopTime.stop.id.id,
                        stopSequence = stopTime.stopSequence,
                        departureTime = stopTime.departureTime.takeIf { it >= 0 }?.let { seconds ->
                            LocalTime.ofSecondOfDay(seconds.toLong())
                        }
                    )
                }

            ParsedTrip(
                routeId = trip.route.id.id,
                tripId = trip.id.id,
                directionId = trip.directionId,
                headsign = trip.tripHeadsign,
                stopTimes = stopTimes
            )
        }

        logger.info("Parsed GTFS feed at {} -> {} routes, {} trips", feedPath, routes.size, trips.size)
        ParsedGtfsData(routes = routes, trips = trips)
    }
}
