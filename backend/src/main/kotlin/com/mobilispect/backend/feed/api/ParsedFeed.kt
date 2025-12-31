package com.mobilispect.backend.feed.api

import com.mobilispect.backend.feed.api.ids.GTFSAgencyId
import com.mobilispect.backend.feed.api.ids.GTFSRouteId
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
  val calendars: List<GTFSCalendar>,
  val calendarDates: List<GTFSCalendarDate>,
)

data class GTFSAgency(
  val agencyId: GTFSAgencyId,
  val name: String,
  val url: String?,
  val timezone: String?,
  val phone: String?,
)

data class GTFSRoute(
  val routeId: GTFSRouteId,
  val agencyId: GTFSAgencyId?,
  val shortName: String?,
  val longName: String?,
  val type: Int?,
)

data class GTFSTrip(
  val routeId: GTFSRouteId,
  val tripId: GTFSTripId,
  val serviceId: String,
  val directionId: Int?,
  val headsign: String?,
  val shapeId: String?,
  val stopTimes: List<GTFSStopTime>,
)

data class GTFSCalendar(
  val serviceId: String,
  val monday: Int,
  val tuesday: Int,
  val wednesday: Int,
  val thursday: Int,
  val friday: Int,
  val saturday: Int,
  val sunday: Int,
  val startDate: java.time.LocalDate,
  val endDate: java.time.LocalDate,
)

data class GTFSCalendarDate(
  val serviceId: String,
  val date: java.time.LocalDate,
  val exceptionType: Int?,
) {
  companion object {
    const val ADDED: Int = 1
    const val REMOVED: Int = 2
  }
}

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
