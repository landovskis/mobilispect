package com.mobilispect.backend.route.batch.variant

import com.mobilispect.backend.route.domain.model.RouteVariant
import com.mobilispect.backend.route.domain.model.ids.VariantHash
import java.security.MessageDigest
import java.time.Instant
import org.slf4j.LoggerFactory
import org.springframework.batch.core.configuration.annotation.StepScope
import org.springframework.batch.infrastructure.item.ItemProcessor
import org.springframework.stereotype.Component

/**
 * Spring Batch ItemProcessor that identifies unique route variants from trip patterns.
 *
 * This processor:
 * 1. Takes RouteVariantInput containing route, trips, and stop metadata
 * 2. Analyzes trip data to identify distinct stop patterns
 * 3. Generates SHA-256 hashes for deterministic variant identification
 * 4. Returns RouteVariantBatch containing all identified variants for the route
 *
 * A route variant is defined by its unique sequence of stops. Multiple trips with the same stop
 * pattern will be collapsed into a single variant.
 *
 * Constitutional Requirements:
 * - FR-006: Identify route variants by unique stop sequences
 * - FR-007: Use SHA-256 hash of stop pattern as variant identifier
 */
@Component
@StepScope
class RouteVariantProcessor : ItemProcessor<RouteVariantInput, RouteVariantBatch> {

  private val logger = LoggerFactory.getLogger(RouteVariantProcessor::class.java)

  override fun process(item: RouteVariantInput): RouteVariantBatch {
    val (route, trips, stopsById) = item

    logger.debug("Processing {} trips for route {}", trips.size, route.id.value)

    val variants = mutableMapOf<String, RouteVariant>()
    var skippedTrips = 0

    trips.forEach { trip ->
      val stopIds = trip.stopTimes.sortedBy { it.stopSequence }.map { it.stopId }

      // Skip trips with fewer than 2 stops
      if (stopIds.size < 2) {
        skippedTrips++
        return@forEach
      }

      val stopValues = stopIds.map { it.value }
      val stopNames = stopValues.map { stopId -> stopsById[stopId]?.name ?: stopId }
      val hash = generateVariantHash(stopValues)

      if (!variants.containsKey(hash.value)) {
        // New variant - create it
        val now = Instant.now()
        val variant =
          RouteVariant(
            id = hash,
            routeId = route.id,
            directionId = trip.directionId,
            headsign = trip.headsign,
            stops = stopValues,
            stopNamePattern = stopNames.joinToString("|"),
            stopCount = stopValues.size,
            firstStopId = stopValues.first(),
            lastStopId = stopValues.last(),
            firstSeen = now,
            lastSeen = now,
          )

        variants[hash.value] = variant
      } else {
        // Existing variant - update lastSeen timestamp
        val existing = variants[hash.value]!!
        variants[hash.value] = existing.copy(lastSeen = Instant.now())
      }
    }

    if (skippedTrips > 0) {
      logger.debug(
        "Skipped {} trips with fewer than 2 stops for route {}",
        skippedTrips,
        route.id.value,
      )
    }

    logger.info(
      "Identified {} variants from {} trips for route {} ({})",
      variants.size,
      trips.size,
      route.shortName ?: route.gtfsRouteId,
      route.id.value,
    )

    return RouteVariantBatch(variants.values.toList())
  }

  /** Generate SHA-256 hash from ordered stop IDs. */
  private fun generateVariantHash(stopIds: List<String>): VariantHash {
    require(stopIds.size >= 2) { "Variant hash requires at least two stops" }
    val concatenated = stopIds.joinToString(separator = "|")
    val digest = MessageDigest.getInstance("SHA-256").digest(concatenated.toByteArray())
    val hex = digest.joinToString("") { "%02x".format(it) }
    return VariantHash(hex)
  }
}
