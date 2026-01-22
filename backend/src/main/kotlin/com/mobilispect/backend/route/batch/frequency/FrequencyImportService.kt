package com.mobilispect.backend.route.batch.frequency

import com.mobilispect.backend.feed.api.GTFSData
import com.mobilispect.backend.feed.api.GTFSTrip
import com.mobilispect.backend.route.domain.model.Frequency
import com.mobilispect.backend.route.domain.model.RouteVariant
import com.mobilispect.backend.route.domain.model.TimePeriod
import com.mobilispect.backend.route.domain.model.ids.VariantHash
import com.mobilispect.backend.route.domain.repository.FrequencyRepository
import com.mobilispect.backend.route.domain.repository.RouteVariantRepository
import com.mobilispect.backend.route.domain.service.FrequencyCalculationService
import com.mobilispect.backend.route.events.FrequencyCalculationCompleted
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import org.slf4j.LoggerFactory
import org.springframework.batch.core.step.StepExecution
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Service

@Service
class FrequencyImportService(
  private val frequencyCalculationService: FrequencyCalculationService,
  private val frequencyRepository: FrequencyRepository,
  private val routeVariantRepository: RouteVariantRepository,
  private val eventPublisher: ApplicationEventPublisher,
) {
  private val logger = LoggerFactory.getLogger(FrequencyImportService::class.java)

  /**
   * Execute frequency import from Spring Batch step execution context.
   *
   * Delegates to [processFrequencies] after extracting data from the context.
   */
  fun execute(stepExecution: StepExecution) {
    val parsedData =
      stepExecution.jobExecution.executionContext.get("parsedData") as? GTFSData
        ?: throw IllegalStateException("ParsedGtfsData not found in job execution context")

    val serviceDateParam = stepExecution.jobParameters.getString("serviceDate")
    val serviceDate = serviceDateParam?.let { LocalDate.parse(it) } ?: LocalDate.now()

    val frequencies = processFrequencies(parsedData, serviceDate)

    // Store results in execution context
    val stepContext = stepExecution.executionContext
    stepContext.putInt("frequenciesProcessed", frequencies.size)

    val jobContext = stepExecution.jobExecution.executionContext
    jobContext.putInt("frequenciesProcessed", frequencies.size)
  }

  /**
   * Process frequencies from parsed GTFS data.
   *
   * This method can be called directly for synchronous processing or from Spring Batch.
   *
   * @param parsedData Parsed GTFS data containing trips
   * @param serviceDate Date for frequency calculation (affects weekday/weekend determination)
   * @param variants Optional list of variants to process (if empty, fetches all from repository)
   * @return List of persisted Frequency entities
   */
  fun processFrequencies(
    parsedData: GTFSData,
    serviceDate: LocalDate = LocalDate.now(),
    variants: List<RouteVariant> = emptyList(),
  ): List<Frequency> {
    val persistedVariants = variants.ifEmpty { routeVariantRepository.findAll() }

    val variantMap =
      persistedVariants
        .associateWith { variant ->
          parsedData.trips.filter { trip -> matchesTripPattern(trip, variant.stopPattern) }
        }
        .filterValues { it.isNotEmpty() }

    val allFrequencies = mutableListOf<Frequency>()
    var frequenciesCreated = 0
    var frequenciesUpdated = 0
    val processedVariants = mutableSetOf<Pair<VariantHash, LocalDate>>()

    variantMap.forEach { (variant, trips) ->
      val frequencies = calculateFrequencies(variant, trips, serviceDate)
      frequencies.forEach { frequency ->
        val existing =
          frequencyRepository.findByVariantAndServiceDateAndTimePeriod(
            variantId = frequency.variantId,
            serviceDate = frequency.serviceDate,
            timePeriod = frequency.timePeriod,
          )

        val saved =
          if (existing.isPresent) {
            frequenciesUpdated++
            val existingFrequency = existing.get()
            frequencyRepository.save(
              Frequency(
                id = existingFrequency.id,
                variantId = frequency.variantId,
                serviceDate = frequency.serviceDate,
                timePeriod = frequency.timePeriod,
                averageHeadway = frequency.averageHeadway,
                minHeadway = frequency.minHeadway,
                maxHeadway = frequency.maxHeadway,
                tripCount = frequency.tripCount,
                isIrregular = frequency.isIrregular,
              )
            )
          } else {
            frequenciesCreated++
            frequencyRepository.save(frequency)
          }

        processedVariants.add(VariantHash(frequency.variantId) to frequency.serviceDate)
        allFrequencies.add(saved)
      }
    }

    processedVariants.forEach { (variantId, date) ->
      eventPublisher.publishEvent(
        FrequencyCalculationCompleted(variantId = variantId, serviceDate = date)
      )
    }

    logger.info(
      "Persisted frequency records (total={}, created={}, updated={})",
      allFrequencies.size,
      frequenciesCreated,
      frequenciesUpdated,
    )

    return allFrequencies
  }

  private fun calculateFrequencies(
    variant: RouteVariant,
    trips: List<GTFSTrip>,
    serviceDate: LocalDate,
  ): List<Frequency> {
    val frequencies = mutableListOf<Frequency>()
    val dayOfWeek = serviceDate.dayOfWeek
    val isWeekend = dayOfWeek == DayOfWeek.SATURDAY || dayOfWeek == DayOfWeek.SUNDAY

    if (isWeekend) {
      val frequency =
        frequencyCalculationService.calculateFrequency(
          variant = variant,
          serviceDate = serviceDate,
          timePeriod = TimePeriod.WEEKEND,
          departureTimes = extractDepartureTimes(trips),
        )
      frequency?.let { frequencies.add(it) }
    } else {
      calculateWeekdayFrequencies(variant, trips, serviceDate, frequencies)
    }

    return frequencies
  }

  private fun calculateWeekdayFrequencies(
    variant: RouteVariant,
    trips: List<GTFSTrip>,
    serviceDate: LocalDate,
    frequencies: MutableList<Frequency>,
  ) {
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

  private fun matchesTripPattern(trip: GTFSTrip, stopPattern: String): Boolean {
    val tripPattern =
      trip.stopTimes.sortedBy { it.stopSequence }.joinToString("|") { it.stopId.value }
    return tripPattern == stopPattern
  }
}
