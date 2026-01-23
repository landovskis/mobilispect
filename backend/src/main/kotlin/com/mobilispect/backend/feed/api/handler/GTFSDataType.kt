package com.mobilispect.backend.feed.api.handler

/**
 * Enumeration of GTFS data types that can be processed by feed data handlers.
 *
 * Each type corresponds to a specific GTFS file and data structure. Handlers can subscribe to
 * multiple data types to receive related data together in a single invocation.
 */
enum class GTFSDataType {
  /** Agency data from agency.txt */
  AGENCY,

  /** Route data from routes.txt */
  ROUTE,

  /** Trip data from trips.txt */
  TRIP,

  /** Stop data from stops.txt */
  STOP,

  /** Shape data from shapes.txt */
  SHAPE,

  /** Stop time data from stop_times.txt */
  STOP_TIME,

  /** Frequency data from frequencies.txt */
  FREQUENCY,

  /** Calendar/service data from calendar.txt and calendar_dates.txt */
  CALENDAR,
}
