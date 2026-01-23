package com.mobilispect.backend.feed.api.handler

import com.mobilispect.backend.feed.api.GTFSAgency
import com.mobilispect.backend.feed.api.GTFSRoute
import com.mobilispect.backend.feed.api.GTFSShapePoint
import com.mobilispect.backend.feed.api.GTFSStop
import com.mobilispect.backend.feed.api.GTFSStopTime
import com.mobilispect.backend.feed.api.GTFSTrip
import com.mobilispect.backend.feed.domain.model.ids.FeedId

/**
 * Bundle containing GTFS data for multi-type handler processing.
 *
 * Handlers declare which data types they need via [FeedDataHandler.dataTypes]. The orchestrator
 * creates a bundle containing the requested data and invokes the handler with all required data
 * available in a single call.
 *
 * This approach eliminates coordination between handlers that need multiple related data types
 * (e.g., routes + trips + shapes for route variants).
 *
 * @property feedId The feed this data belongs to
 * @property agencies Agency data from agency.txt
 * @property routes Route data from routes.txt
 * @property trips Trip data from trips.txt
 * @property stops Stop data from stops.txt
 * @property shapes Shape data from shapes.txt, keyed by shape_id
 * @property stopTimes Stop time data from stop_times.txt
 * @property frequencies Frequency data from frequencies.txt
 * @property calendars Calendar data from calendar.txt/calendar_dates.txt, keyed by service_id
 */
data class GTFSDataBundle(
  val feedId: FeedId,
  val agencies: List<GTFSAgency> = emptyList(),
  val routes: List<GTFSRoute> = emptyList(),
  val trips: List<GTFSTrip> = emptyList(),
  val stops: List<GTFSStop> = emptyList(),
  val shapes: Map<String, List<GTFSShapePoint>> = emptyMap(),
  val stopTimes: List<GTFSStopTime> = emptyList(),
  val frequencies: List<GTFSFrequency> = emptyList(),
  val calendars: Map<String, GTFSCalendar> = emptyMap(),
) {
  /**
   * Checks if a specific data type has data in this bundle.
   *
   * @param type The GTFS data type to check
   * @return true if the bundle contains non-empty data for the specified type
   */
  fun has(type: GTFSDataType): Boolean {
    return when (type) {
      GTFSDataType.AGENCY -> agencies.isNotEmpty()
      GTFSDataType.ROUTE -> routes.isNotEmpty()
      GTFSDataType.TRIP -> trips.isNotEmpty()
      GTFSDataType.STOP -> stops.isNotEmpty()
      GTFSDataType.SHAPE -> shapes.isNotEmpty()
      GTFSDataType.STOP_TIME -> stopTimes.isNotEmpty()
      GTFSDataType.FREQUENCY -> frequencies.isNotEmpty()
      GTFSDataType.CALENDAR -> calendars.isNotEmpty()
    }
  }
}

/**
 * GTFS frequency entry from frequencies.txt.
 *
 * @property tripId Trip this frequency applies to
 * @property startTime Start time of the frequency-based service
 * @property endTime End time of the frequency-based service
 * @property headwaySecs Time between departures in seconds
 * @property exactTimes Whether times are exactly scheduled (1) or approximate (0)
 */
data class GTFSFrequency(
  val tripId: String,
  val startTime: String,
  val endTime: String,
  val headwaySecs: Int,
  val exactTimes: Int? = null,
)

/**
 * GTFS calendar entry from calendar.txt.
 *
 * @property serviceId Service identifier
 * @property monday Whether service runs on Mondays
 * @property tuesday Whether service runs on Tuesdays
 * @property wednesday Whether service runs on Wednesdays
 * @property thursday Whether service runs on Thursdays
 * @property friday Whether service runs on Fridays
 * @property saturday Whether service runs on Saturdays
 * @property sunday Whether service runs on Sundays
 * @property startDate Start date of the service (YYYYMMDD)
 * @property endDate End date of the service (YYYYMMDD)
 */
data class GTFSCalendar(
  val serviceId: String,
  val monday: Boolean,
  val tuesday: Boolean,
  val wednesday: Boolean,
  val thursday: Boolean,
  val friday: Boolean,
  val saturday: Boolean,
  val sunday: Boolean,
  val startDate: String,
  val endDate: String,
)
