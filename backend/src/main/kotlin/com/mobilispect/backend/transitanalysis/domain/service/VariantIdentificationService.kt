package com.mobilispect.backend.transitanalysis.domain.service

import com.mobilispect.backend.transitanalysis.domain.model.Route
import com.mobilispect.backend.transitanalysis.domain.model.RouteVariant
import com.mobilispect.backend.transitanalysis.infrastructure.gtfs.ParsedTrip
import org.springframework.stereotype.Service
import java.time.Instant

/**
 * Service for identifying unique route variants from trip patterns.
 *
 * A route variant is defined by its unique sequence of stops.
 * This service analyzes trip data to identify distinct stop patterns
 * and generates SHA-256 hashes for deterministic variant identification.
 *
 * Constitutional Requirements:
 * - FR-006: Identify route variants by unique stop sequences
 * - FR-007: Use SHA-256 hash of stop pattern as variant identifier
 */
interface VariantIdentificationService {
    fun identifyVariants(route: Route, trips: List<ParsedTrip>): List<RouteVariant>
    fun identifyVariants(tripsByRoute: Map<Route, List<ParsedTrip>>): List<RouteVariant>
}

@Service
class VariantIdentificationServiceImpl(
    private val variantHashGenerator: VariantHashGenerator
) : VariantIdentificationService {
    override fun identifyVariants(route: Route, trips: List<ParsedTrip>): List<RouteVariant> {
        val variants = mutableMapOf<String, RouteVariant>()
        trips.forEach { trip ->
            val stopIds = trip.stopTimes.sortedBy { it.stopSequence }.map { it.stopId }
            if (stopIds.size < 2) return@forEach
            val hash = variantHashGenerator.fromStops(stopIds)
            variants.computeIfAbsent(hash.value) {
                RouteVariant(
                    id = hash,
                    route = route,
                    directionId = trip.directionId,
                    headsign = trip.headsign,
                    stopPattern = stopIds.joinToString("|"),
                    stopCount = stopIds.size,
                    firstStopId = stopIds.first(),
                    lastStopId = stopIds.last()
                )
            }.apply {
                lastSeen = Instant.now()
            }
        }
        return variants.values.toList()
    }

    override fun identifyVariants(tripsByRoute: Map<Route, List<ParsedTrip>>): List<RouteVariant> =
        tripsByRoute.flatMap { (route, trips) -> identifyVariants(route, trips) }
}
