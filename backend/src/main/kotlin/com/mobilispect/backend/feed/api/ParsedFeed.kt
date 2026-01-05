package com.mobilispect.backend.feed.api

import com.mobilispect.backend.feed.api.ids.FeedLocalAgencyId
import com.mobilispect.backend.feed.api.ids.FeedLocalRouteId
import com.mobilispect.backend.feed.api.ids.GTFSStopId
import com.mobilispect.backend.feed.api.ids.GTFSTripId
import java.time.LocalTime

/**
 * Parsed GTFS payload distilled for variant identification and frequency calculations. The
 * structure intentionally stays lightweight and decoupled from the OneBusAway classes.
 */
data class GTFSData(
  val agencies: List<GTFSAgency>,
  val routes: List<GTFSRoute>,
  val trips: List<GTFSTrip>,
  val stops: List<GTFSStop>,
  val shapes: Map<String, List<GTFSShapePoint>>,
)

data class GTFSAgency(
  val agencyId: FeedLocalAgencyId,
  val name: String,
  val url: String?,
  val timezone: String?,
  val phone: String?,
)

data class GTFSRoute(
  val routeId: FeedLocalRouteId,
  val agencyId: FeedLocalAgencyId?,
  val shortName: String?,
  val longName: String?,
  val type: Int?,
)

data class GTFSTrip(
  val routeId: FeedLocalRouteId,
  val tripId: GTFSTripId,
  val directionId: Int?,
  val headsign: String?,
  val shapeId: String?,
  val stopTimes: List<GTFSStopTime>,
)

data class GTFSStopTime(
  val stopId: GTFSStopId,
  val stopSequence: Int,
  val departureTime: LocalTime?,
  val shapeDistTraveledKm: Double?,
)

data class GTFSStop(
  val stopId: GTFSStopId,
  val name: String?,
  val latitude: Double?,
  val longitude: Double?,
  val stopCode: String? = null,
  val stopDesc: String? = null,
  val zoneId: String? = null,
  val stopUrl: String? = null,
  val locationType: Int? = null,
  val parentStation: String? = null,
)

data class GTFSShapePoint(
  val latitude: Double,
  val longitude: Double,
  val sequence: Int,
  val distTraveledKm: Double?,
)
