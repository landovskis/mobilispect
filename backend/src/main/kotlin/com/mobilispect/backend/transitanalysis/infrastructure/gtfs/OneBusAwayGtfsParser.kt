package com.mobilispect.backend.transitanalysis.infrastructure.gtfs

import org.slf4j.LoggerFactory
import java.nio.file.Path
import java.time.LocalTime

/**
 * OneBusAway-backed GTFS parser.
 *
 * Keeps the output lean by mapping only the fields needed by the transit-analysis
 * services (routes, trips, stop times).
 *
 * DISABLED: OneBusAway library has compilation issues.
 * Using StubGtfsParser temporarily until OneBusAway dependency is properly configured.
 */
// @Component - DISABLED until OneBusAway library is properly configured
class OneBusAwayGtfsParser /* : GtfsParser */ {
    private val logger = LoggerFactory.getLogger(OneBusAwayGtfsParser::class.java)

    /*
     * IMPLEMENTATION DISABLED - see StubGtfsParser.kt

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
    */
}
