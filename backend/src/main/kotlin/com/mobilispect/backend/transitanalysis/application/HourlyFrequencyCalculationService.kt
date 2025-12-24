package com.mobilispect.backend.transitanalysis.application

import com.mobilispect.backend.transitanalysis.api.dto.HourlyFrequencyDTO
import com.mobilispect.backend.transitanalysis.api.dto.RouteHourlyFrequencyDTO
import com.mobilispect.backend.transitanalysis.domain.model.ids.RouteId
import com.mobilispect.backend.transitanalysis.domain.model.ids.VariantHash
import com.mobilispect.backend.transitanalysis.domain.repository.RouteRepository
import com.mobilispect.backend.transitanalysis.domain.repository.RouteVariantRepository
import com.mobilispect.backend.transitanalysis.infrastructure.gtfs.GtfsParser
import com.mobilispect.backend.transitanalysis.infrastructure.gtfs.ParsedTrip
import com.mobilispect.backend.agency.domain.repository.AgencyRepository
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import java.nio.file.Path
import java.nio.file.Paths
import java.time.Duration
import java.time.LocalDate
import java.time.LocalTime
import kotlin.math.abs

/**
 * Service for calculating hourly transit frequency (headway) metrics.
 *
 * Calculates average, minimum, and maximum headways for route variants
 * in 24 1-hour intervals (00:00-01:00, 01:00-02:00, ..., 23:00-24:00).
 *
 * This provides finer granularity than the standard TimePeriod-based frequency data,
 * enabling visualization of service patterns throughout the day.
 */
interface HourlyFrequencyCalculationService {
    /**
     * Calculate hourly frequencies for a specific route on a given service date.
     *
     * Aggregates frequency metrics across all variants of the route, providing
     * a route-level view of service frequency for each hour of the day.
     *
     * @param routeId Onestop ID of the route
     * @param serviceDate Date for which to calculate frequencies
     * @return List of 24 hourly frequency records (0-23), may have zero trips for hours without service
     */
    fun calculateRouteHourlyFrequencies(
        routeId: RouteId,
        serviceDate: LocalDate
    ): List<RouteHourlyFrequencyDTO>

    /**
     * Calculate hourly frequencies for a specific route variant on a given service date.
     *
     * Provides variant-specific frequency metrics for each hour of the day.
     *
     * @param variantHash SHA-256 hash identifying the variant
     * @param serviceDate Date for which to calculate frequencies
     * @return List of 24 hourly frequency records (0-23), may have zero trips for hours without service
     */
    fun calculateVariantHourlyFrequencies(
        variantHash: VariantHash,
        serviceDate: LocalDate
    ): List<HourlyFrequencyDTO>
}

@Service
class HourlyFrequencyCalculationServiceImpl(
    private val gtfsParser: GtfsParser,
    private val routeRepository: RouteRepository,
    private val routeVariantRepository: RouteVariantRepository,
    private val agencyRepository: AgencyRepository,
    @Value("\${app.gtfs.download-directory:./data/gtfs}") private val gtfsDownloadDirectory: String
) : HourlyFrequencyCalculationService {

    private val logger = LoggerFactory.getLogger(HourlyFrequencyCalculationServiceImpl::class.java)

    override fun calculateRouteHourlyFrequencies(
        routeId: RouteId,
        serviceDate: LocalDate
    ): List<RouteHourlyFrequencyDTO> {
        val route = routeRepository.findById(routeId)
            ?: run {
                logger.warn("Route not found: {}", routeId)
                return emptyList()
            }
        val agency = agencyRepository.findById(route.agencyId)
            ?: run {
                logger.warn("Agency not found for route {}: {}", routeId, route.agencyId)
                return emptyList()
            }

        // Get GTFS feed path for this route's agency
        val feedOnestopId = agency.feedId.value
        val gtfsPath = resolveGtfsPath(feedOnestopId)

        // Parse GTFS feed
        val parsedData = gtfsParser.parse(gtfsPath).getOrElse { exception ->
            logger.error("Failed to parse GTFS feed for route {}: {}", routeId, exception.message, exception)
            return emptyList()
        }

        // Get all variants for this route
        val variants = routeVariantRepository.findByRouteId(route.id)

        if (variants.isEmpty()) {
            logger.warn("No variants found for route: {}", routeId)
            return emptyList()
        }

        // Get all trips for this route
        val routeTrips = parsedData.trips.filter { it.routeId == route.gtfsRouteId }

        // Calculate hourly frequencies for each variant
        val variantHourlyData: List<List<HourlyFrequencyDTO>> = variants.map { variant ->
            val variantTrips = routeTrips.filter { trip ->
                matchesTripPattern(trip, variant.stopPattern)
            }
            calculateHourlyFrequenciesForTrips(variantTrips, variant.id.value, serviceDate)
        }

        // Aggregate across all variants for route-level view
        return aggregateRouteHourlyFrequencies(variantHourlyData, routeId.value, serviceDate)
    }

    override fun calculateVariantHourlyFrequencies(
        variantHash: VariantHash,
        serviceDate: LocalDate
    ): List<HourlyFrequencyDTO> {
        val variant = routeVariantRepository.findById(variantHash)
            ?: run {
                logger.warn("Variant not found: {}", variantHash)
                return emptyList()
            }
        val route = routeRepository.findById(variant.routeId)
            ?: run {
                logger.warn("Route not found for variant {}: {}", variantHash, variant.routeId)
                return emptyList()
            }
        val agency = agencyRepository.findById(route.agencyId)
            ?: run {
                logger.warn("Agency not found for route {}: {}", variant.routeId, route.agencyId)
                return emptyList()
            }

        // Get GTFS feed path for this variant's route's agency
        val feedOnestopId = agency.feedId.value
        val gtfsPath = resolveGtfsPath(feedOnestopId)

        // Parse GTFS feed
        val parsedData = gtfsParser.parse(gtfsPath).getOrElse { exception ->
            logger.error("Failed to parse GTFS feed for variant {}: {}", variantHash, exception.message, exception)
            return emptyList()
        }

        // Filter trips that match this variant's stop pattern
        val variantTrips = parsedData.trips.filter { trip ->
            trip.routeId == route.gtfsRouteId && matchesTripPattern(trip, variant.stopPattern)
        }

        return calculateHourlyFrequenciesForTrips(variantTrips, variantHash.value, serviceDate)
    }

    /**
     * Calculate hourly frequencies for a list of trips.
     *
     * Groups trips by hour of day based on first stop departure time,
     * then calculates headway statistics for each hour.
     */
    private fun calculateHourlyFrequenciesForTrips(
        trips: List<ParsedTrip>,
        variantId: String,
        serviceDate: LocalDate
    ): List<HourlyFrequencyDTO> {
        // Extract departure times (first stop of each trip)
        val departureTimes = trips.mapNotNull { trip ->
            trip.stopTimes.firstOrNull()?.departureTime
        }

        // Group by hour of day (0-23)
        val tripsByHour = departureTimes.groupBy { it.hour }

        // Calculate frequency for each hour (0-23)
        return (0..23).map { hour ->
            val hourlyDepartures = tripsByHour[hour] ?: emptyList()
            calculateHourlyFrequency(hour, hourlyDepartures, variantId, serviceDate)
        }
    }

    /**
     * Calculate frequency metrics for a single hour.
     *
     * Uses same logic as FrequencyCalculationService but for a specific hour.
     */
    private fun calculateHourlyFrequency(
        hourOfDay: Int,
        departureTimes: List<LocalTime>,
        variantId: String,
        serviceDate: LocalDate
    ): HourlyFrequencyDTO {
        if (departureTimes.isEmpty()) {
            return HourlyFrequencyDTO(
                variantId = variantId,
                serviceDate = serviceDate.toString(),
                hourOfDay = hourOfDay,
                averageHeadwayMinutes = null,
                minHeadwayMinutes = null,
                maxHeadwayMinutes = null,
                tripCount = 0,
                isIrregular = false
            )
        }

        val sorted = departureTimes.sorted()
        val allHeadways = sorted.zipWithNext { a, b -> Duration.between(a, b).toMinutes().toDouble() }

        // Filter out zero or near-zero headways (data anomalies where buses depart simultaneously)
        val headways = allHeadways.filter { it > 0.0 }

        if (headways.isEmpty()) {
            return HourlyFrequencyDTO(
                variantId = variantId,
                serviceDate = serviceDate.toString(),
                hourOfDay = hourOfDay,
                averageHeadwayMinutes = null,
                minHeadwayMinutes = null,
                maxHeadwayMinutes = null,
                tripCount = departureTimes.size,
                isIrregular = true
            )
        }

        val min = headways.minOrNull() ?: 0.0
        val max = headways.maxOrNull() ?: 0.0
        val average = headways.average()

        // Determine if schedule is irregular (large variance in headways)
        val irregular = abs(max - min) > average

        return HourlyFrequencyDTO(
            variantId = variantId,
            serviceDate = serviceDate.toString(),
            hourOfDay = hourOfDay,
            averageHeadwayMinutes = if (irregular) null else average,
            minHeadwayMinutes = if (headways.isNotEmpty()) min else null,
            maxHeadwayMinutes = if (headways.isNotEmpty()) max else null,
            tripCount = departureTimes.size,
            isIrregular = irregular
        )
    }

    /**
     * Aggregate hourly frequencies across multiple variants for route-level view.
     *
     * Combines metrics from all variants operating during each hour.
     */
    private fun aggregateRouteHourlyFrequencies(
        variantHourlyData: List<List<HourlyFrequencyDTO>>,
        routeId: String,
        serviceDate: LocalDate
    ): List<RouteHourlyFrequencyDTO> {
        return (0..23).map { hour ->
            val hourDataAcrossVariants = variantHourlyData.flatMap { variantData ->
                variantData.filter { it.hourOfDay == hour }
            }

            val totalTrips = hourDataAcrossVariants.sumOf { it.tripCount }
            val activeVariants = hourDataAcrossVariants.count { it.tripCount > 0 }
            val hasIrregular = hourDataAcrossVariants.any { it.isIrregular }

            // Aggregate headways from all variants
            val allMinHeadways = hourDataAcrossVariants.mapNotNull { it.minHeadwayMinutes }
            val allMaxHeadways = hourDataAcrossVariants.mapNotNull { it.maxHeadwayMinutes }
            val allAverageHeadways = hourDataAcrossVariants.mapNotNull { it.averageHeadwayMinutes }

            RouteHourlyFrequencyDTO(
                routeId = routeId,
                serviceDate = serviceDate.toString(),
                hourOfDay = hour,
                averageHeadwayMinutes = if (allAverageHeadways.isNotEmpty()) allAverageHeadways.average() else null,
                minHeadwayMinutes = allMinHeadways.minOrNull(),
                maxHeadwayMinutes = allMaxHeadways.maxOrNull(),
                tripCount = totalTrips,
                variantCount = activeVariants,
                isIrregular = hasIrregular
            )
        }
    }

    /**
     * Check if a trip matches a variant's stop pattern.
     *
     * For MVP, we use a simple equality check on the stop pattern.
     * In production, this might need more sophisticated matching.
     */
    private fun matchesTripPattern(trip: ParsedTrip, stopPattern: String): Boolean {
        val tripPattern = trip.stopTimes
            .sortedBy { it.stopSequence }
            .joinToString("|") { it.stopId }
        return tripPattern == stopPattern
    }

    /**
     * Resolve GTFS file path for a feed.
     *
     * For MVP, assumes GTFS ZIP files are stored in {downloadDirectory}/{feedId}.zip
     */
    private fun resolveGtfsPath(feedOnestopId: String): Path {
        return Paths.get(gtfsDownloadDirectory, "$feedOnestopId.zip")
    }
}
