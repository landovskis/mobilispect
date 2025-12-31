package com.mobilispect.backend.route.batch.hourly

import com.mobilispect.backend.feed.api.GTFSCalendar
import com.mobilispect.backend.feed.api.GTFSCalendarDate
import com.mobilispect.backend.feed.api.GTFSData
import com.mobilispect.backend.feed.api.GTFSTrip
import com.mobilispect.backend.route.domain.model.RouteVariant
import com.mobilispect.backend.route.domain.model.ServiceDayType
import com.mobilispect.backend.route.domain.repository.RouteVariantRepository
import java.time.DayOfWeek
import java.time.LocalDate
import org.slf4j.LoggerFactory
import org.springframework.batch.core.annotation.BeforeStep
import org.springframework.batch.core.configuration.annotation.StepScope
import org.springframework.batch.core.step.StepExecution
import org.springframework.batch.infrastructure.item.ItemReader
import org.springframework.stereotype.Component

/**
 * Spring Batch ItemReader that groups trips by route for hourly stats calculation.
 *
 * This reader:
 * 1. Retrieves ParsedGtfsData from the job execution context
 * 2. Fetches persisted RouteVariant entities from the database
 * 3. Matches variants to their trips using stop pattern
 * 4. Groups variants with trips by route ID and yields RouteHourlyStatsInput per route
 */
@Component
@StepScope
class RouteHourlyStatsReader(private val routeVariantRepository: RouteVariantRepository) :
  ItemReader<RouteHourlyStatsInput> {

  private val logger = LoggerFactory.getLogger(RouteHourlyStatsReader::class.java)

  private var variantIterator: Iterator<Map.Entry<String, Map<RouteVariant, List<GTFSTrip>>>>? =
    null
  private var serviceDayTypes: Map<String, Set<ServiceDayType>> = emptyMap()

  @BeforeStep
  fun beforeStep(stepExecution: StepExecution) {
    val parsedData =
      stepExecution.jobExecution.executionContext.get("parsedData") as? GTFSData
        ?: throw IllegalStateException("ParsedGtfsData not found in job execution context")

    logger.info(
      "Initializing RouteHourlyStatsReader with {} trips from GTFS data",
      parsedData.trips.size,
    )

    serviceDayTypes = buildServiceDayTypes(parsedData.calendars, parsedData.calendarDates)

    val persistedVariants = routeVariantRepository.findAll()
    logger.info("Fetched {} persisted route variants from database", persistedVariants.size)

    val variantTrips =
      persistedVariants
        .associateWith { variant ->
          parsedData.trips.filter { trip -> matchesTripPattern(trip, variant.stops) }
        }
        .filterValues { it.isNotEmpty() }

    val groupedByRoute =
      variantTrips.entries
        .groupBy({ it.key.routeId.value }) { it.key to it.value }
        .mapValues { entry -> entry.value.toMap() }

    variantIterator = groupedByRoute.entries.iterator()

    logger.info(
      "Prepared {} routes for hourly stats calculation ({} variants had no trips)",
      groupedByRoute.size,
      persistedVariants.size - variantTrips.size,
    )
  }

  override fun read(): RouteHourlyStatsInput? {
    if (variantIterator == null || !variantIterator!!.hasNext()) {
      return null
    }

    val (routeId, variantTrips) = variantIterator!!.next()

    return RouteHourlyStatsInput(
      routeId = routeId,
      variantTrips = variantTrips,
      serviceDayTypes = serviceDayTypes,
    )
  }

  private fun matchesTripPattern(trip: GTFSTrip, variantStops: List<String>): Boolean {
    val tripPattern = trip.stopTimes.sortedBy { it.stopSequence }.map { it.stopId.value }
    return tripPattern == variantStops
  }

  private fun buildServiceDayTypes(
    calendars: List<GTFSCalendar>,
    calendarDates: List<GTFSCalendarDate>,
  ): Map<String, Set<ServiceDayType>> {
    val byServiceId = calendars.associateBy { it.serviceId }.toMutableMap()
    val datesByServiceId = calendarDates.groupBy { it.serviceId }
    val serviceIds = (byServiceId.keys + datesByServiceId.keys).toSet()

    return serviceIds.associateWith { serviceId ->
      val dayTypes = mutableSetOf<ServiceDayType>()
      val calendar = byServiceId[serviceId]
      if (calendar != null) {
        if (hasWeekday(calendar)) {
          dayTypes.add(ServiceDayType.WEEKDAY)
        }
        if (calendar.saturday == 1) {
          dayTypes.add(ServiceDayType.SATURDAY)
        }
        if (calendar.sunday == 1) {
          dayTypes.add(ServiceDayType.SUNDAY)
        }
      }

      val addedDates =
        datesByServiceId[serviceId]?.filter { it.exceptionType == GTFSCalendarDate.ADDED }
          ?: emptyList()
      if (addedDates.any { calendar == null || !isCalendarActiveOn(calendar, it.date) }) {
        dayTypes.add(ServiceDayType.HOLIDAY)
      }

      if (dayTypes.isEmpty()) {
        dayTypes.add(ServiceDayType.WEEKDAY)
      }
      dayTypes
    }
  }

  private fun hasWeekday(calendar: GTFSCalendar): Boolean {
    return calendar.monday == 1 ||
      calendar.tuesday == 1 ||
      calendar.wednesday == 1 ||
      calendar.thursday == 1 ||
      calendar.friday == 1
  }

  private fun isCalendarActiveOn(calendar: GTFSCalendar, date: LocalDate): Boolean {
    if (date.isBefore(calendar.startDate) || date.isAfter(calendar.endDate)) {
      return false
    }
    return when (date.dayOfWeek) {
      DayOfWeek.MONDAY -> calendar.monday == 1
      DayOfWeek.TUESDAY -> calendar.tuesday == 1
      DayOfWeek.WEDNESDAY -> calendar.wednesday == 1
      DayOfWeek.THURSDAY -> calendar.thursday == 1
      DayOfWeek.FRIDAY -> calendar.friday == 1
      DayOfWeek.SATURDAY -> calendar.saturday == 1
      DayOfWeek.SUNDAY -> calendar.sunday == 1
    }
  }
}
