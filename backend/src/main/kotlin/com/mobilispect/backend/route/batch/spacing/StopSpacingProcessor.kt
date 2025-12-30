package com.mobilispect.backend.route.batch.spacing

import com.mobilispect.backend.feed.api.GTFSStop
import com.mobilispect.backend.route.domain.model.StopSpacing
import java.time.Instant
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import org.slf4j.LoggerFactory
import org.springframework.batch.core.configuration.annotation.StepScope
import org.springframework.batch.infrastructure.item.ItemProcessor
import org.springframework.stereotype.Component

/**
 * Spring Batch ItemProcessor that calculates stop spacing for route variants.
 *
 * This processor:
 * 1. Takes StopSpacingInput containing a variant and stop location data
 * 2. Extracts stop coordinates from the variant's stop pattern
 * 3. Calculates distances between each pair of consecutive stops using Haversine formula
 * 4. Creates individual StopSpacing records for each consecutive stop pair
 * 5. Returns StopSpacingBatch containing all spacing records for the variant
 *
 * The Haversine formula calculates great-circle distances between points on a sphere, providing
 * accurate distance measurements for geographic coordinates.
 */
@Component
@StepScope
class StopSpacingProcessor : ItemProcessor<StopSpacingInput, StopSpacingBatch> {

  private val logger = LoggerFactory.getLogger(StopSpacingProcessor::class.java)

  override fun process(item: StopSpacingInput): StopSpacingBatch? {
    val (variant, stopsById) = item

    // Calculate spacing for each consecutive stop pair
    val spacings = calculateStopSpacings(variant, stopsById)

    if (spacings.isEmpty()) {
      logger.debug(
        "Variant {} has no valid stop spacing data (insufficient coordinates)",
        variant.id.value,
      )
      return null
    }

    logger.debug(
      "Calculated {} stop spacing records for variant {}",
      spacings.size,
      variant.id.value,
    )

    return StopSpacingBatch(spacings)
  }

  /**
   * Calculate spacing between each consecutive stop pair using Haversine formula. Returns list of
   * StopSpacing entities, or empty list if cannot be calculated.
   */
  private fun calculateStopSpacings(
    variant: com.mobilispect.backend.route.domain.model.RouteVariant,
    stopsById: Map<String, GTFSStop>,
  ): List<StopSpacing> {
    // Extract stop IDs from stored list
    val stopIds = variant.stops

    if (stopIds.size < 2) {
      logger.debug("Variant {} has insufficient stops ({} stops)", variant.id.value, stopIds.size)
      return emptyList()
    }

    val calculatedAt = Instant.now()
    val spacings = mutableListOf<StopSpacing>()

    // Calculate distance between each consecutive stop pair
    for (i in 0 until stopIds.size - 1) {
      val fromStopId = stopIds[i]
      val toStopId = stopIds[i + 1]

      val fromStop = stopsById[fromStopId]
      val toStop = stopsById[toStopId]

      if (
        fromStop?.latitude != null &&
          fromStop.longitude != null &&
          toStop?.latitude != null &&
          toStop.longitude != null
      ) {
        val distanceMeters =
          haversineDistanceMeters(
            fromStop.latitude,
            fromStop.longitude,
            toStop.latitude,
            toStop.longitude,
          )

        spacings.add(
          StopSpacing(
            variantId = variant.id.value,
            fromStopId = fromStopId,
            toStopId = toStopId,
            stopSequence = i,
            distanceMeters = distanceMeters,
            calculatedAt = calculatedAt,
          )
        )

        logger.trace(
          "  [{}/{}] {} -> {}: {} m",
          i + 1,
          stopIds.size - 1,
          fromStopId,
          toStopId,
          "%.0f".format(distanceMeters),
        )
      } else {
        logger.debug(
          "  [{}/{}] Skipping {} -> {} (missing coordinates)",
          i + 1,
          stopIds.size - 1,
          fromStopId,
          toStopId,
        )
      }
    }

    if (spacings.isNotEmpty()) {
      val distances = spacings.map { it.distanceMeters }
      logger.debug(
        "Variant {} spacing summary: {} segments, min={} m, max={} m, avg={} m",
        variant.id.value,
        spacings.size,
        "%.0f".format(distances.minOrNull() ?: 0.0),
        "%.0f".format(distances.maxOrNull() ?: 0.0),
        "%.0f".format(distances.average()),
      )
    }

    return spacings
  }

  /** Calculate distance between two points using Haversine formula. Returns distance in meters. */
  private fun haversineDistanceMeters(
    lat1: Double,
    lon1: Double,
    lat2: Double,
    lon2: Double,
  ): Double {
    val earthRadiusKm = 6371.0
    val dLat = Math.toRadians(lat2 - lat1)
    val dLon = Math.toRadians(lon2 - lon1)
    val a =
      sin(dLat / 2) * sin(dLat / 2) +
        cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLon / 2) * sin(dLon / 2)
    val c = 2 * atan2(sqrt(a), sqrt(1 - a))
    val distanceKm = earthRadiusKm * c
    return distanceKm * 1000.0 // Convert to meters
  }
}
