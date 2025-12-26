package com.mobilispect.backend.route.domain.service

import com.mobilispect.backend.route.domain.model.Route
import com.mobilispect.backend.route.domain.model.RouteVariant
import com.mobilispect.backend.transitanalysis.infrastructure.gtfs.ParsedTrip
import com.mobilispect.backend.transitanalysis.domain.service.StopSpacingCalculationService
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
    fun identifyVariants(
        route: Route,
        trips: List<ParsedTrip>,
        stopsById: Map<String, com.mobilispect.backend.transitanalysis.infrastructure.gtfs.ParsedStop>,
        shapesById: Map<String, List<com.mobilispect.backend.transitanalysis.infrastructure.gtfs.ParsedShapePoint>>
    ): List<RouteVariant>
    fun identifyVariants(
        tripsByRoute: Map<Route, List<ParsedTrip>>,
        stopsById: Map<String, com.mobilispect.backend.transitanalysis.infrastructure.gtfs.ParsedStop>,
        shapesById: Map<String, List<com.mobilispect.backend.transitanalysis.infrastructure.gtfs.ParsedShapePoint>>
    ): List<RouteVariant>
}

@Service
class VariantIdentificationServiceImpl(
    private val variantHashGenerator: VariantHashGenerator,
    private val stopSpacingCalculationService: StopSpacingCalculationService
) : VariantIdentificationService {
    override fun identifyVariants(
        route: Route,
        trips: List<ParsedTrip>,
        stopsById: Map<String, com.mobilispect.backend.transitanalysis.infrastructure.gtfs.ParsedStop>,
        shapesById: Map<String, List<com.mobilispect.backend.transitanalysis.infrastructure.gtfs.ParsedShapePoint>>
    ): List<RouteVariant> {
        val variants = mutableMapOf<String, RouteVariant>()
        trips.forEach { trip ->
            val stopIds = trip.stopTimes.sortedBy { it.stopSequence }.map { it.stopId }
            if (stopIds.size < 2) return@forEach
            val stopNames = stopIds.map { stopId -> stopsById[stopId]?.name ?: stopId }
            val hash = variantHashGenerator.fromStops(stopIds)
            if (!variants.containsKey(hash.value)) {
                val averageStopSpacingKm = stopSpacingCalculationService.calculateAverageSpacingKm(
                    trip = trip,
                    stopsById = stopsById,
                    shapesById = shapesById
                )
                val now = Instant.now()
                val variant = RouteVariant(
                    id = hash,
                    routeId = route.id,
                    directionId = trip.directionId,
                    headsign = trip.headsign,
                    stopPattern = stopIds.joinToString("|"),
                    stopNamePattern = stopNames.joinToString("|"),
                    stopCount = stopIds.size,
                    firstStopId = stopIds.first(),
                    lastStopId = stopIds.last(),
                    averageStopSpacingKm = averageStopSpacingKm,
                    firstSeen = now,
                    lastSeen = now
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
        tripsByRoute: Map<Route, List<ParsedTrip>>,
        stopsById: Map<String, com.mobilispect.backend.transitanalysis.infrastructure.gtfs.ParsedStop>,
        shapesById: Map<String, List<com.mobilispect.backend.transitanalysis.infrastructure.gtfs.ParsedShapePoint>>
    ): List<RouteVariant> =
        tripsByRoute.flatMap { (route, trips) -> identifyVariants(route, trips, stopsById, shapesById) }
}
