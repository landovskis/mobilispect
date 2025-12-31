package com.mobilispect.backend.route.batch.hourly

import com.mobilispect.backend.feed.api.GTFSTrip
import com.mobilispect.backend.route.domain.model.RouteHourlyStat
import com.mobilispect.backend.route.domain.model.RouteVariant
import com.mobilispect.backend.route.domain.model.ServiceDayType
import com.mobilispect.backend.route.domain.repository.StopSpacingRepository
import java.time.LocalDate
import java.time.LocalTime
import org.slf4j.LoggerFactory
import org.springframework.batch.core.configuration.annotation.StepScope
import org.springframework.batch.infrastructure.item.ItemProcessor
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component

/** Spring Batch ItemProcessor that calculates hourly trip counts and average speeds per route. */
@Component
@StepScope
class RouteHourlyStatsProcessor(
  private val stopSpacingRepository: StopSpacingRepository,
  @Value("#{jobParameters['serviceDate'] ?: null}") private val serviceDateParam: String?,
) : ItemProcessor<RouteHourlyStatsInput, RouteHourlyStatsBatch> {

  private val logger = LoggerFactory.getLogger(RouteHourlyStatsProcessor::class.java)

  override fun process(item: RouteHourlyStatsInput): RouteHourlyStatsBatch {
    val serviceDate = serviceDateParam?.let { LocalDate.parse(it) } ?: LocalDate.now()
    val routeId = item.routeId

    val hourBucketsByDayType =
      mutableMapOf<ServiceDayType, MutableMap<Short?, Array<HourAccumulator>>>()
    val distanceByVariant = buildDistanceByVariant(item.variantTrips.keys)

    item.variantTrips.forEach { (variant, trips) ->
      val distanceMeters = distanceByVariant[variant.id.value] ?: 0.0
      val directionId = variant.directionId?.toShort()
      trips.forEach { trip ->
        val dayTypes =
          item.serviceDayTypes[trip.serviceId]?.takeIf { it.isNotEmpty() }
            ?: setOf(ServiceDayType.WEEKDAY)
        val departure = trip.stopTimes.firstOrNull()?.departureTime ?: return@forEach
        val hour = departure.hour
        dayTypes.forEach { dayType ->
          val directionBuckets =
            hourBucketsByDayType
              .getOrPut(dayType) { mutableMapOf() }
              .getOrPut(directionId) { Array(24) { HourAccumulator() } }
          val bucket = directionBuckets[hour]
          bucket.tripCount++
          val speedKph = computeTripSpeedKph(trip, distanceMeters)
          if (speedKph != null) {
            bucket.speedSumKph += speedKph
            bucket.speedSamples++
          }
        }
      }
    }

    val orderedDayTypes = ServiceDayType.entries
    val stats =
      orderedDayTypes.flatMap { dayType ->
        val directionBuckets = hourBucketsByDayType[dayType] ?: return@flatMap emptyList()
        val orderedDirections =
          directionBuckets.keys.sortedWith(
            compareBy<Short?> { it == null }.thenBy { it ?: Short.MAX_VALUE }
          )
        orderedDirections.flatMap { directionId ->
          directionBuckets.getValue(directionId).mapIndexedNotNull { hour, bucket ->
            if (bucket.tripCount == 0) {
              null
            } else {
              RouteHourlyStat(
                routeId = routeId,
                directionId = directionId,
                dayType = dayType,
                serviceDate = serviceDate,
                hourOfDay = hour,
                tripCount = bucket.tripCount,
                averageSpeedKph =
                  if (bucket.speedSamples > 0) bucket.speedSumKph / bucket.speedSamples else null,
              )
            }
          }
        }
      }

    logger.debug("Calculated {} hourly stats for route {} on {}", stats.size, routeId, serviceDate)

    return RouteHourlyStatsBatch(stats)
  }

  private fun buildDistanceByVariant(variants: Collection<RouteVariant>): Map<String, Double> {
    return variants.associate { variant ->
      val spacing = stopSpacingRepository.findByVariantOrderBySequence(variant.id.value)
      val distance = spacing.sumOf { it.distanceMeters }
      variant.id.value to distance
    }
  }

  private fun computeTripSpeedKph(trip: GTFSTrip, distanceMeters: Double): Double? {
    if (distanceMeters <= 0.0) {
      return null
    }

    val orderedTimes = trip.stopTimes.sortedBy { it.stopSequence }
    val firstDeparture = orderedTimes.firstOrNull()?.departureTime
    val lastDeparture = orderedTimes.lastOrNull()?.departureTime
    if (firstDeparture == null || lastDeparture == null) {
      return null
    }

    val durationSeconds = computeDurationSeconds(firstDeparture, lastDeparture)
    if (durationSeconds <= 0) {
      return null
    }

    return (distanceMeters / durationSeconds) * 3.6
  }

  private fun computeDurationSeconds(start: LocalTime, end: LocalTime): Int {
    val startSeconds = start.toSecondOfDay()
    val endSeconds = end.toSecondOfDay()
    val raw = endSeconds - startSeconds
    return when {
      raw > 0 -> raw
      raw < 0 -> raw + 86400
      else -> 0
    }
  }

  private data class HourAccumulator(
    var tripCount: Int = 0,
    var speedSumKph: Double = 0.0,
    var speedSamples: Int = 0,
  )
}
