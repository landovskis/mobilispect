package com.mobilispect.backend.schedule.gtfs

import com.mobilispect.backend.util.LocalDateSerializer
import java.time.LocalDate
import kotlinx.serialization.Serializable

@Serializable
data class GTFSCalendar(
  val service_id: String,
  val monday: Int,
  val tuesday: Int,
  val wednesday: Int,
  val thursday: Int,
  val friday: Int,
  val saturday: Int,
  val sunday: Int,
  @Serializable(with = LocalDateSerializer::class) val start_date: LocalDate,
  @Serializable(with = LocalDateSerializer::class) val end_date: LocalDate,
)
