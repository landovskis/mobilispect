package com.mobilispect.backend.route.handler

import com.mobilispect.backend.feed.api.GTFSStop
import com.mobilispect.backend.feed.api.handler.FeedDataHandler
import com.mobilispect.backend.feed.api.handler.GTFSDataBundle
import com.mobilispect.backend.feed.api.handler.GTFSDataType
import com.mobilispect.backend.feed.api.handler.ImportContext
import com.mobilispect.backend.feed.api.handler.ImportError
import com.mobilispect.backend.feed.api.handler.ImportResult
import com.mobilispect.backend.feed.domain.model.ids.FeedId
import com.mobilispect.backend.route.domain.model.RouteVariant
import com.mobilispect.backend.route.domain.model.StopSpacing
import com.mobilispect.backend.route.domain.repository.RouteVariantRepository
import com.mobilispect.backend.route.domain.repository.StopSpacingRepository
import java.time.Instant
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

/**
 * Handler that calculates and persists stop spacing for route variants.
 *
 * This handler:
 * 1. Fetches all persisted route variants from the database
 * 2. Extracts stop coordinates from the GTFS data bundle
 * 3. Calculates distances between consecutive stops using Haversine formula
 * 4. Persists StopSpacing records for each variant
 *
 * Priority is set to 3 (after route variants at 4) because spacings depend on variants.
 *
 * @param stopSpacingRepository Repository for persisting stop spacing entities
 * @param routeVariantRepository Repository for fetching route variant entities
 */
@Component
class StopSpacingFeedDataHandler(
  private val stopSpacingRepository: StopSpacingRepository,
  private val routeVariantRepository: RouteVariantRepository,
) : FeedDataHandler {

  private val logger = LoggerFactory.getLogger(StopSpacingFeedDataHandler::class.java)

  override fun dataTypes(): Set<GTFSDataType> = setOf(GTFSDataType.STOP)

  override fun priority(): Int = 3

  @Transactional
  override fun handle(feedId: FeedId, data: GTFSDataBundle, context: ImportContext): ImportResult {
    if (data.stops.isEmpty()) {
      logger.debug(
        "No stops in bundle for feed {}, skipping stop spacing calculation",
        feedId.value,
      )
      return ImportResult.Success(0)
    }

    val stopsById = data.stops.associateBy { it.stopId.value }
    val variants = routeVariantRepository.findAll()

    if (variants.isEmpty()) {
      logger.debug(
        "No route variants found for feed {}, skipping stop spacing calculation",
        feedId.value,
      )
      return ImportResult.Success(0)
    }

    logger.info(
      "Calculating stop spacing for {} variants using {} stops for feed {}",
      variants.size,
      stopsById.size,
      feedId.value,
    )

    var variantsProcessed = 0
    val errors = mutableListOf<ImportError>()

    variants.forEach { variant ->
      try {
        val spacings = calculateStopSpacings(variant, stopsById)

        if (spacings.isEmpty()) {
          logger.debug(
            "Variant {} has no valid stop spacing data (insufficient coordinates)",
            variant.id.value,
          )
          return@forEach
        }

        // Delete existing spacing records for this variant to avoid duplicates
        if (stopSpacingRepository.existsByVariant(variant.id.value)) {
          logger.debug("Deleting existing spacing records for variant {}", variant.id.value)
          stopSpacingRepository.deleteByVariant(variant.id.value)
        }

        // Save all spacing records for this variant
        stopSpacingRepository.saveAll(spacings)

        val distances = spacings.map { it.distanceMeters }
        logger.debug(
          "Created {} spacing records for variant {} (avg: {} m, min: {} m, max: {} m)",
          spacings.size,
          variant.id.value.take(12),
          "%.0f".format(distances.average()),
          "%.0f".format(distances.minOrNull() ?: 0.0),
          "%.0f".format(distances.maxOrNull() ?: 0.0),
        )

        variantsProcessed++
      } catch (e: Exception) {
        logger.error(
          "Failed to calculate stop spacing for variant {}: {}",
          variant.id.value.take(12),
          e.message,
        )
        errors.add(
          ImportError(
            recordId = variant.id.value,
            message = e.message ?: "Unknown error",
            exception = e,
          )
        )
      }
    }

    logger.info(
      "Processed stop spacing for {} variants for feed {}",
      variantsProcessed,
      feedId.value,
    )

    return when {
      errors.isEmpty() -> ImportResult.Success(variantsProcessed)
      variantsProcessed > 0 -> ImportResult.PartialSuccess(variantsProcessed, errors)
      else -> ImportResult.Failure(errors.first())
    }
  }

  /**
   * Calculate spacing between each consecutive stop pair using Haversine formula.
   *
   * @param variant The route variant with stop pattern
   * @param stopsById Map of stop ID to GTFSStop for coordinate lookup
   * @return List of StopSpacing entities, or empty list if cannot be calculated
   */
  private fun calculateStopSpacings(
    variant: RouteVariant,
    stopsById: Map<String, GTFSStop>,
  ): List<StopSpacing> {
    val stopIds = variant.stopPattern.split("|")

    if (stopIds.size < 2) {
      logger.debug("Variant {} has insufficient stops ({} stops)", variant.id.value, stopIds.size)
      return emptyList()
    }

    val calculatedAt = Instant.now()
    val spacings = mutableListOf<StopSpacing>()

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

    return spacings
  }

  /**
   * Calculate distance between two points using Haversine formula.
   *
   * @param lat1 Latitude of first point in degrees
   * @param lon1 Longitude of first point in degrees
   * @param lat2 Latitude of second point in degrees
   * @param lon2 Longitude of second point in degrees
   * @return Distance in meters
   */
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
