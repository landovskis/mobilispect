package com.mobilispect.backend.schedule.gtfs

import kotlinx.serialization.Serializable

@Serializable
class GTFSStopTime(
  val trip_id: String,
  val stop_id: String,
  val arrival_time: String,
  val departure_time: String,
  val stop_sequence: Int?,
)
