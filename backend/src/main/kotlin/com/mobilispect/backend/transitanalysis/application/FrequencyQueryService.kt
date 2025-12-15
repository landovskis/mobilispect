package com.mobilispect.backend.transitanalysis.application

import com.mobilispect.backend.config.RedisConfiguration
import com.mobilispect.backend.transitanalysis.api.dto.FrequencyDTO
import com.mobilispect.backend.transitanalysis.api.dto.HourlyFrequencyDTO
import com.mobilispect.backend.transitanalysis.api.dto.RouteDTO
import com.mobilispect.backend.transitanalysis.api.dto.RouteHourlyFrequencyDTO
import com.mobilispect.backend.transitanalysis.api.dto.RouteVariantDTO
import com.mobilispect.backend.transitanalysis.domain.model.ids.RouteId
import com.mobilispect.backend.transitanalysis.domain.model.ids.VariantHash
import com.mobilispect.backend.transitanalysis.domain.repository.FrequencyRepository
import com.mobilispect.backend.transitanalysis.domain.repository.RouteRepository
import com.mobilispect.backend.transitanalysis.domain.repository.RouteVariantRepository
import org.springframework.cache.annotation.Cacheable
import org.springframework.stereotype.Service
import java.time.LocalDate

/**
 * Query service for frequency-related operations with Redis caching (T123).
 *
 * All query methods are cached with 1-hour TTL to improve performance.
 * Hourly frequency methods use 24-hour TTL due to expensive calculation.
 * Cache is invalidated when feed imports complete (T124).
 */
@Service
class FrequencyQueryService(
    private val routeRepository: RouteRepository,
    private val routeVariantRepository: RouteVariantRepository,
    private val frequencyRepository: FrequencyRepository,
    private val hourlyFrequencyCalculationService: HourlyFrequencyCalculationService
) {
    @Cacheable(value = [RedisConfiguration.FREQUENCY_CACHE], key = "'route_' + #routeId")
    fun getRoute(routeId: RouteId): RouteDTO? =
        routeRepository.findById(routeId).orElse(null)?.let {
            RouteDTO(
                id = it.id.value,
                agencyId = it.agency.agencyOnestopId.value,
                shortName = it.shortName,
                longName = it.longName,
                routeType = it.routeType,
                active = it.active
            )
        }

    @Cacheable(value = [RedisConfiguration.FREQUENCY_CACHE], key = "'variants_' + #routeId")
    fun getVariantsByRoute(routeId: RouteId): List<RouteVariantDTO> =
        routeVariantRepository.findByRouteId(routeId).map {
            RouteVariantDTO(
                id = it.id.value,
                routeId = it.route.id.value,
                directionId = it.directionId,
                headsign = it.headsign,
                stopCount = it.stopCount,
                stopPattern = it.stopPattern,
                firstStopId = it.firstStopId,
                lastStopId = it.lastStopId
            )
    }

    @Cacheable(
        value = [RedisConfiguration.FREQUENCY_CACHE],
        key = "'freq_' + #variantHash + '_' + (#serviceDate != null ? #serviceDate : 'all')"
    )
    fun getFrequenciesForVariant(variantHash: VariantHash, serviceDate: LocalDate?): List<FrequencyDTO> {
        val variant = routeVariantRepository.findById(variantHash).orElse(null) ?: return emptyList()
        val freqs = if (serviceDate != null) {
            frequencyRepository.findByVariantAndServiceDate(variant, serviceDate, org.springframework.data.domain.Pageable.unpaged()).content
        } else {
            frequencyRepository.findByVariant(variant, org.springframework.data.domain.Pageable.unpaged()).content
        }
        return freqs.map {
            FrequencyDTO(
                id = it.id.toString(),
                variantId = it.variant.id.value,
                serviceDate = it.serviceDate.toString(),
                timePeriod = it.timePeriod,
                averageHeadwayMinutes = it.averageHeadway,
                minHeadwayMinutes = it.minHeadway,
                maxHeadwayMinutes = it.maxHeadway,
                tripCount = it.tripCount,
                isIrregular = it.isIrregular
            )
        }
    }

    /**
     * Get hourly frequencies for a route on a specific service date.
     *
     * Returns 24 hourly frequency records (0-23), providing granular
     * frequency data throughout the day. Cached with 24-hour TTL due to
     * expensive calculation.
     *
     * @param routeId Onestop ID of the route
     * @param serviceDate Date for which to calculate frequencies
     * @return List of 24 hourly frequency records
     */
    @Cacheable(
        value = [RedisConfiguration.FREQUENCY_CACHE],
        key = "'hourly_route_' + #routeId + '_' + #serviceDate"
    )
    fun getHourlyFrequenciesForRoute(
        routeId: RouteId,
        serviceDate: LocalDate
    ): List<RouteHourlyFrequencyDTO> {
        return hourlyFrequencyCalculationService.calculateRouteHourlyFrequencies(routeId, serviceDate)
    }

    /**
     * Get hourly frequencies for a specific variant on a specific service date.
     *
     * Returns 24 hourly frequency records (0-23) for a single variant,
     * providing detailed frequency patterns throughout the day. Cached with
     * 24-hour TTL due to expensive calculation.
     *
     * @param variantHash SHA-256 hash identifying the variant
     * @param serviceDate Date for which to calculate frequencies
     * @return List of 24 hourly frequency records
     */
    @Cacheable(
        value = [RedisConfiguration.FREQUENCY_CACHE],
        key = "'hourly_variant_' + #variantHash + '_' + #serviceDate"
    )
    fun getHourlyFrequenciesForVariant(
        variantHash: VariantHash,
        serviceDate: LocalDate
    ): List<HourlyFrequencyDTO> {
        return hourlyFrequencyCalculationService.calculateVariantHourlyFrequencies(variantHash, serviceDate)
    }
}
