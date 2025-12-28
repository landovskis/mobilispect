package com.mobilispect.backend.feed.api

import com.mobilispect.backend.feed.api.ids.GTFSAgencyId
import com.mobilispect.backend.feed.api.ids.GTFSRouteId
import java.time.LocalTime


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
    val agencyId: GTFSAgencyId,
    val name: String,
    val url: String?,
    val timezone: String?,
    val phone: String?
)

data class ParsedRoute(
    val routeId: GTFSRouteId,
    val agencyId: GTFSAgencyId?,
    val shortName: String?,
    val longName: String?,
    val type: Int?
)

data class ParsedTrip(
    val routeId: GTFSRouteId,
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
    val longitude: Double?,
    val stopCode: String? = null,
    val stopDesc: String? = null,
    val zoneId: String? = null,
    val stopUrl: String? = null,
    val locationType: Int? = null,
    val parentStation: String? = null
)

data class ParsedShapePoint(
    val latitude: Double,
    val longitude: Double,
    val sequence: Int,
    val distTraveledKm: Double?
)
