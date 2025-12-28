package com.mobilispect.backend.schedule.gtfs

import com.mobilispect.backend.schedule.ScheduledTrip
import com.mobilispect.backend.schedule.ScheduledTripDataSource
import com.mobilispect.backend.schedule.route.RouteIDDataSource
import com.mobilispect.backend.util.readTextAndNormalize
import java.io.IOException
import java.nio.file.Path
import java.time.DayOfWeek
import java.time.LocalDate
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerializationException
import kotlinx.serialization.csv.Csv
import kotlinx.serialization.decodeFromString
import org.slf4j.Logger
import org.slf4j.LoggerFactory

@OptIn(ExperimentalSerializationApi::class)
/** A [ScheduledTripDataSource] that uses a GTFS feed as a source for [ScheduledTrip]s. */
internal class GTFSScheduledTripDataSource(private val routeIDDataSource: RouteIDDataSource) :
  ScheduledTripDataSource {
  private val logger: Logger = LoggerFactory.getLogger(GTFSScheduledTripDataSource::class.java)

  override fun trips(
    extractedDir: Path,
    version: String,
    feedID: String,
  ): Result<Collection<ScheduledTrip>> =
    routeIDDataSource.routeIDs(feedID).map { routeIDs ->
      return try {
        val csv = Csv {
          hasHeaderRecord = true
          ignoreUnknownColumns = true
        }

        val calendarExceptions = findCalendarExceptions(extractedDir, csv)
        val calendars = findCalendars(extractedDir, csv)

        val tripsIn = extractedDir.resolve("trips.txt").toFile().readTextAndNormalize()
        val trips: Collection<GTFSTrip>
        val decodingTime =
          kotlin.time.measureTime { trips = csv.decodeFromString<Collection<GTFSTrip>>(tripsIn) }
        logger.debug("Decoded {} trips in {}", trips.size, decodingTime)

        Result.success(
          trips.take(100).mapNotNull { trip ->
            val added =
              calendarExceptions[trip.service_id]
                ?.filter { it.exception_type == GTFSCalendarDate.ADDED }
                ?.map { it.date } ?: emptyList()
            val removed =
              calendarExceptions[trip.service_id]
                ?.filter { it.exception_type == GTFSCalendarDate.REMOVED }
                ?.map { it.date } ?: emptyList()
            val dates = findDates(calendars, trip, removed, added)
            val routeID = routeIDs[trip.route_id] ?: return@mapNotNull null
            ScheduledTrip(
              uid = trip.trip_id,
              routeID = routeID,
              direction = trip.trip_headsign,
              versions = listOf(version),
              dates = dates,
            )
          }
        )
      } catch (e: IOException) {
        Result.failure(e)
      } catch (e: SerializationException) {
        Result.failure(e)
      }
    }

  private fun findDates(
    calendars: Map<String, GTFSCalendar>,
    trip: GTFSTrip,
    removed: List<LocalDate>,
    added: List<LocalDate>,
  ): List<LocalDate> {
    val dates =
      calendars[trip.service_id]
        ?.let { calendar ->
          listOf(
              allDayOfWeekInRange(
                calendar,
                calendar.start_date,
                calendar.end_date,
                DayOfWeek.MONDAY,
              ) {
                it.monday == 1
              },
              allDayOfWeekInRange(
                calendar,
                calendar.start_date,
                calendar.end_date,
                DayOfWeek.TUESDAY,
              ) {
                it.tuesday == 1
              },
              allDayOfWeekInRange(
                calendar,
                calendar.start_date,
                calendar.end_date,
                DayOfWeek.WEDNESDAY,
              ) {
                it.wednesday == 1
              },
              allDayOfWeekInRange(
                calendar,
                calendar.start_date,
                calendar.end_date,
                DayOfWeek.THURSDAY,
              ) {
                it.thursday == 1
              },
              allDayOfWeekInRange(
                calendar,
                calendar.start_date,
                calendar.end_date,
                DayOfWeek.FRIDAY,
              ) {
                it.friday == 1
              },
              allDayOfWeekInRange(
                calendar,
                calendar.start_date,
                calendar.end_date,
                DayOfWeek.SATURDAY,
              ) {
                it.saturday == 1
              },
              allDayOfWeekInRange(
                calendar,
                calendar.start_date,
                calendar.end_date,
                DayOfWeek.SUNDAY,
              ) {
                it.sunday == 1
              },
            )
            .flatten()
        }
        ?.filter { !removed.contains(it) } ?: emptyList()
    return (dates + added).sorted()
  }

  private fun findCalendars(extractedDir: Path, csv: Csv): Map<String, GTFSCalendar> {
    val calendarIn = extractedDir.resolve("calendar.txt").toFile().readTextAndNormalize()
    val calendars =
      csv.decodeFromString<Collection<GTFSCalendar>>(calendarIn).associateBy { calendar ->
        calendar.service_id
      }
    logger.debug("Imported calendars: {}", calendars)
    return calendars
  }

  private fun findCalendarExceptions(
    extractedDir: Path,
    csv: Csv,
  ): Map<String, List<GTFSCalendarDate>> {
    val calendarDatesIn = extractedDir.resolve("calendar_dates.txt").toFile().readTextAndNormalize()
    val calendarDates =
      csv.decodeFromString<Collection<GTFSCalendarDate>>(calendarDatesIn).groupBy { calendarDate ->
        calendarDate.service_id
      }
    logger.debug("Imported calendar dates: {}", calendarDates)
    return calendarDates
  }

  private fun allDayOfWeekInRange(
    calendar: GTFSCalendar,
    startDate: LocalDate,
    endDate: LocalDate,
    dayOfWeek: DayOfWeek,
    predicate: (GTFSCalendar) -> Boolean,
  ): List<LocalDate> {
    val dates = mutableListOf<LocalDate>()
    if (predicate(calendar)) {
      var date = findFirstInRange(startDate, endDate, dayOfWeek) ?: return emptyList()
      dates += date

      date = date.plusWeeks(1)
      while (!date.isAfter(endDate)) {
        dates += date
        date = date.plusWeeks(1)
      }
    }
    return dates
  }

  private fun findFirstInRange(
    startDate: LocalDate,
    endDate: LocalDate,
    dayOfWeek: DayOfWeek,
  ): LocalDate? {
    var date = startDate
    while (date.dayOfWeek != dayOfWeek) {
      if (date.isAfter(endDate)) {
        return null
      }
      date = date.plusDays(1)
    }
    return date
  }
}
