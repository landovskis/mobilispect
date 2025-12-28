package com.mobilispect.backend.route.domain.service

import com.mobilispect.backend.feed.api.GTFSShapePoint
import com.mobilispect.backend.feed.api.GTFSStop
import com.mobilispect.backend.feed.api.GTFSTrip
import com.mobilispect.backend.route.domain.model.Route
import com.mobilispect.backend.route.domain.model.RouteVariant
import java.time.Instant
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import org.springframework.stereotype.Service

/**
 * Service for identifying unique route variants from trip patterns.
 *
 * A route variant is defined by its unique sequence of stops. This service analyzes trip data to
 * identify distinct stop patterns and generates SHA-256 hashes for deterministic variant
 * identification.
 *
 * Constitutional Requirements:
 * - FR-006: Identify route variants by unique stop sequences
 * - FR-007: Use SHA-256 hash of stop pattern as variant identifier
 */
interface VariantIdentificationService {
  fun identifyVariants(
    route: Route,
    trips: List<GTFSTrip>,
    stopsById: Map<String, GTFSStop>,
    shapesById: Map<String, List<GTFSShapePoint>>,
  ): List<RouteVariant>

  fun identifyVariants(
    tripsByRoute: Map<Route, List<GTFSTrip>>,
    stopsById: Map<String, GTFSStop>,
    shapesById: Map<String, List<GTFSShapePoint>>,
  ): List<RouteVariant>
}

@Service
class VariantIdentificationServiceImpl(private val variantHashGenerator: VariantHashGenerator) :
  VariantIdentificationService {
  override fun identifyVariants(
    route: Route,
    trips: List<GTFSTrip>,
    stopsById: Map<String, GTFSStop>,
    shapesById: Map<String, List<GTFSShapePoint>>,
  ): List<RouteVariant> {
    val variants = mutableMapOf<String, RouteVariant>()
    trips.forEach { trip ->
      val stopIds = trip.stopTimes.sortedBy { it.stopSequence }.map { it.stopId }
      if (stopIds.size < 2) return@forEach
      val stopNames = stopIds.map { stopId -> stopsById[stopId.value]?.name ?: stopId.value }
      val hash = variantHashGenerator.fromStops(stopIds.map { it.value })
      if (!variants.containsKey(hash.value)) {
        val now = Instant.now()
        val variant =
          RouteVariant(
            id = hash,
            routeId = route.id,
            directionId = trip.directionId,
            headsign = trip.headsign,
            stopPattern = stopIds.joinToString("|") { it.value },
            stopNamePattern = stopNames.joinToString("|"),
            stopCount = stopIds.size,
            firstStopId = stopIds.first().value,
            lastStopId = stopIds.last().value,
            firstSeen = now,
            lastSeen = now,
          )
        variants[hash.value] = variant
      } else {
        // Update lastSeen for existing variant
        val existing = variants[hash.value]!!
        variants[hash.value] = existing.copy(lastSeen = Instant.now())
      }
    }
    return variants.values.toList()
  }

  override fun identifyVariants(
    tripsByRoute: Map<Route, List<GTFSTrip>>,
    stopsById: Map<String, GTFSStop>,
    shapesById: Map<String, List<GTFSShapePoint>>,
  ): List<RouteVariant> =
    tripsByRoute.flatMap { (route, trips) -> identifyVariants(route, trips, stopsById, shapesById) }

  /**
   * Calculate average spacing between consecutive stops using Haversine formula. Returns average
   * distance in kilometers, or null if cannot be calculated.
   */
  private fun calculateAverageStopSpacingKm(
    trip: GTFSTrip,
    stopsById: Map<String, GTFSStop>,
  ): Double? {
    val stopCoordinates =
      trip.stopTimes
        .sortedBy { it.stopSequence }
        .mapNotNull { stopTime ->
          stopsById[stopTime.stopId.value]?.let { stop ->
            if (stop.latitude != null && stop.longitude != null) {
              Pair(stop.latitude, stop.longitude)
            } else null
          }
        }

    if (stopCoordinates.size < 2) return null

    val distances =
      stopCoordinates.zipWithNext { (lat1, lon1), (lat2, lon2) ->
        haversineDistanceKm(lat1, lon1, lat2, lon2)
      }

    return if (distances.isNotEmpty()) distances.average() else null
  }

  /**
   * Calculate distance between two points using Haversine formula. Returns distance in kilometers.
   */
  private fun haversineDistanceKm(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
    val earthRadiusKm = 6371.0
    val dLat = Math.toRadians(lat2 - lat1)
    val dLon = Math.toRadians(lon2 - lon1)
    val a =
      sin(dLat / 2) * sin(dLat / 2) +
        cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLon / 2) * sin(dLon / 2)
    val c = 2 * atan2(sqrt(a), sqrt(1 - a))
    return earthRadiusKm * c
  }
}
