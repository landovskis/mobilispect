package com.mobilispect.backend.schedule.gtfs

import com.mobilispect.backend.util.LocalDateSerializer
import java.time.LocalDate
import kotlinx.serialization.Serializable

@Serializable
data class GTFSCalendarDate(
  val service_id: String,
  @Serializable(with = LocalDateSerializer::class) val date: LocalDate,
  val exception_type: Int?,
) {
  companion object {
    const val ADDED: Int = 1
    const val REMOVED: Int = 2
  }
}
