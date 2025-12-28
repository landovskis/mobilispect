package com.mobilispect.backend.schedule

import com.mobilispect.backend.util.DateTimeOffset

data class ScheduledStop(
  /** A globally unique trip ID. */
  val tripID: String,

  /** A globally unique stop ID (in OneStop ID format). */
  val stopID: String,
  val departsAt: DateTimeOffset? = null,
  val arrivesAt: DateTimeOffset? = null,
  val stopSequence: Int?,
  val versions: Collection<String>,
)
