package com.mobilispect.backend.route.batch.frequency

import com.mobilispect.backend.feed.api.GTFSTrip
import com.mobilispect.backend.route.domain.model.Frequency
import com.mobilispect.backend.route.domain.model.TimePeriod
import com.mobilispect.backend.route.domain.service.FrequencyCalculationService
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import org.slf4j.LoggerFactory
import org.springframework.batch.core.configuration.annotation.StepScope
import org.springframework.batch.infrastructure.item.ItemProcessor
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component

/**
 * Spring Batch ItemProcessor that calculates frequency metrics for route variants.
 *
 * This processor:
 * 1. Takes FrequencyInput containing a variant and its trips
 * 2. Groups trips by time period (AM peak, PM peak, off-peak, weekend)
 * 3. Calculates frequency metrics for each time period
 * 4. Returns FrequencyBatch containing Frequency domain models
 *
 * Frequency calculation is delegated to FrequencyCalculationService.
 */
@Component
@StepScope
class FrequencyProcessor(
  private val frequencyCalculationService: FrequencyCalculationService,
  @Value("#{jobParameters['serviceDate'] ?: null}") private val serviceDateParam: String?,
) : ItemProcessor<FrequencyInput, FrequencyBatch> {

  private val logger = LoggerFactory.getLogger(FrequencyProcessor::class.java)

  override fun process(item: FrequencyInput): FrequencyBatch {
    val (variant, trips) = item

    // Determine service date (from job parameter or use current date)
    val serviceDate = serviceDateParam?.let { LocalDate.parse(it) } ?: LocalDate.now()

    logger.debug(
      "Processing frequency for variant {} with {} trips on service date {}",
      variant.id.value,
      trips.size,
      serviceDate,
    )

    // Calculate frequencies for each time period
    val frequencies = mutableListOf<Frequency>()

    // Determine day type for time period filtering
    val dayOfWeek = serviceDate.dayOfWeek
    val isWeekend = dayOfWeek == DayOfWeek.SATURDAY || dayOfWeek == DayOfWeek.SUNDAY

    if (isWeekend) {
      // Weekend: calculate for all trips
      val departureTimes = extractDepartureTimes(trips)
      val frequency =
        frequencyCalculationService.calculateFrequency(
          variant = variant,
          serviceDate = serviceDate,
          timePeriod = TimePeriod.WEEKEND,
          departureTimes = departureTimes,
        )
      frequency?.let { frequencies.add(it) }
    } else {
      // Weekday: calculate for each time period
      calculateWeekdayFrequencies(variant, trips, serviceDate, frequencies)
    }

    logger.debug(
      "Calculated {} frequency records for variant {}",
      frequencies.size,
      variant.id.value,
    )

    return FrequencyBatch(frequencies)
  }

  private fun calculateWeekdayFrequencies(
    variant: com.mobilispect.backend.route.domain.model.RouteVariant,
    trips: List<GTFSTrip>,
    serviceDate: LocalDate,
    frequencies: MutableList<Frequency>,
  ) {
    // AM Peak: 6:00 AM - 9:00 AM
    val amPeakStart = LocalTime.of(6, 0)
    val amPeakEnd = LocalTime.of(9, 0)
    val amPeakTrips = filterTripsByTimeRange(trips, amPeakStart, amPeakEnd)
    if (amPeakTrips.isNotEmpty()) {
      val frequency =
        frequencyCalculationService.calculateFrequency(
          variant = variant,
          serviceDate = serviceDate,
          timePeriod = TimePeriod.WEEKDAY_AM_PEAK,
          departureTimes = extractDepartureTimes(amPeakTrips),
        )
      frequency?.let { frequencies.add(it) }
    }

    // PM Peak: 4:00 PM - 7:00 PM
    val pmPeakStart = LocalTime.of(16, 0)
    val pmPeakEnd = LocalTime.of(19, 0)
    val pmPeakTrips = filterTripsByTimeRange(trips, pmPeakStart, pmPeakEnd)
    if (pmPeakTrips.isNotEmpty()) {
      val frequency =
        frequencyCalculationService.calculateFrequency(
          variant = variant,
          serviceDate = serviceDate,
          timePeriod = TimePeriod.WEEKDAY_PM_PEAK,
          departureTimes = extractDepartureTimes(pmPeakTrips),
        )
      frequency?.let { frequencies.add(it) }
    }

    // Off-Peak: all other weekday times
    val offPeakTrips =
      trips.filter { trip ->
        val departureTime = trip.stopTimes.firstOrNull()?.departureTime ?: return@filter false
        !isInTimeRange(departureTime, amPeakStart, amPeakEnd) &&
          !isInTimeRange(departureTime, pmPeakStart, pmPeakEnd)
      }
    if (offPeakTrips.isNotEmpty()) {
      val frequency =
        frequencyCalculationService.calculateFrequency(
          variant = variant,
          serviceDate = serviceDate,
          timePeriod = TimePeriod.WEEKDAY_OFF_PEAK,
          departureTimes = extractDepartureTimes(offPeakTrips),
        )
      frequency?.let { frequencies.add(it) }
    }
  }

  private fun filterTripsByTimeRange(
    trips: List<GTFSTrip>,
    startTime: LocalTime,
    endTime: LocalTime,
  ): List<GTFSTrip> {
    return trips.filter { trip ->
      val departureTime = trip.stopTimes.firstOrNull()?.departureTime ?: return@filter false
      isInTimeRange(departureTime, startTime, endTime)
    }
  }

  private fun isInTimeRange(time: LocalTime, start: LocalTime, end: LocalTime): Boolean {
    return time >= start && time < end
  }

  private fun extractDepartureTimes(trips: List<GTFSTrip>): List<LocalTime> {
    return trips.mapNotNull { trip -> trip.stopTimes.firstOrNull()?.departureTime }
  }
}
