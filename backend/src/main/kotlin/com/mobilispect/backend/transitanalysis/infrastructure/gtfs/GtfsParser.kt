package com.mobilispect.backend.transitanalysis.infrastructure.gtfs

import java.nio.file.Path
import java.time.LocalTime

/**
 * GTFS feed parser interface.
 *
 * Responsible for parsing GTFS ZIP archives and extracting transit data
 * using the OneBusAway GTFS library.
 *
 * Constitutional Requirements:
 * - FR-002: Parse and extract route, trip, stop, and schedule information
 *
 * Module Boundary: Infrastructure layer in transit-analysis module
 */
interface GtfsParser {

    /**
     * Parse a GTFS feed archive.
     *
     * @param feedPath Path to the GTFS ZIP file
     * @return Result containing parsed data on success, or error on failure
     */
    fun parse(feedPath: Path): Result<ParsedGtfsData>
}

/**
 * Parsed GTFS payload distilled for variant identification and frequency
 * calculations. The structure intentionally stays lightweight and decoupled
 * from the OneBusAway classes.
 */
data class ParsedGtfsData(
    val agencies: List<ParsedAgency>,
    val routes: List<ParsedRoute>,
    val trips: List<ParsedTrip>,
    val stops: List<ParsedStop>,
    val shapes: Map<String, List<ParsedShapePoint>>
)

data class ParsedAgency(
    val agencyId: String,
    val name: String,
    val url: String?,
    val timezone: String?,
    val phone: String?
)

data class ParsedRoute(
    val routeId: String,
    val agencyId: String?,
    val shortName: String?,
    val longName: String?,
    val type: Int?
)

data class ParsedTrip(
    val routeId: String,
    val tripId: String,
    val directionId: Int?,
    val headsign: String?,
    val shapeId: String?,
    val stopTimes: List<ParsedStopTime>
)

data class ParsedStopTime(
    val stopId: String,
    val stopSequence: Int,
    val departureTime: LocalTime?,
    val shapeDistTraveledKm: Double?
)

data class ParsedStop(
    val stopId: String,
    val name: String?,
    val latitude: Double?,
    val longitude: Double?
)

data class ParsedShapePoint(
    val latitude: Double,
    val longitude: Double,
    val sequence: Int,
    val distTraveledKm: Double?
)
