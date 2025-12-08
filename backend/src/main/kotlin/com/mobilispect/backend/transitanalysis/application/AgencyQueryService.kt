package com.mobilispect.backend.transitanalysis.application

import com.mobilispect.backend.config.RedisConfiguration
import com.mobilispect.backend.transitanalysis.api.dto.AgencyDTO
import com.mobilispect.backend.transitanalysis.api.dto.AgencySummaryDTO
import com.mobilispect.backend.feed.model.ids.RegionId
import com.mobilispect.backend.feed.repository.FeedRepository
import com.mobilispect.backend.transitanalysis.domain.model.RouteType
import com.mobilispect.backend.transitanalysis.domain.model.ids.AgencyId
import com.mobilispect.backend.transitanalysis.domain.repository.AgencyRepository
import com.mobilispect.backend.transitanalysis.domain.repository.RouteRepository
import org.springframework.cache.annotation.Cacheable
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Service

/**
 * Query service for agency-related operations with Redis caching (T096).
 *
 * All query methods are cached with 24-hour TTL to improve performance.
 * Cache is automatically invalidated when feed imports complete.
 */
@Service
class AgencyQueryService(
    private val agencyRepository: AgencyRepository,
    private val routeRepository: RouteRepository,
    private val feedRepository: FeedRepository
) {
    /**
     * Get all agencies with pagination.
     * Cached with 24-hour TTL (T096).
     */
    @Cacheable(value = [RedisConfiguration.AGENCY_CACHE], key = "'all_' + #pageable.pageNumber + '_' + #pageable.pageSize")
    fun getAgencies(pageable: Pageable): Page<AgencyDTO> {
        val agencies = agencyRepository.findAll(pageable)
        return agencies.map { agency ->
            mapAgency(agency)
        }
    }

    /**
     * Get agencies for a specific region with pagination, sorted by route count.
     * Cached with 24-hour TTL (T096).
     */
    @Cacheable(
        value = [RedisConfiguration.AGENCY_CACHE],
        key = "'region_' + #regionId.value + '_' + #pageable.pageNumber + '_' + #pageable.pageSize"
    )
    fun getAgenciesByRegion(regionId: RegionId, pageable: Pageable): Page<AgencyDTO> {
        val feeds = feedRepository.findAllByRegionRegionOnestopId(regionId)
        val agencies = feeds.flatMap { feed ->
            agencyRepository.findByFeed(feed, pageable).content
        }
        val sorted = agencies.sortedByDescending { agency ->
            routeRepository.countByAgency(agency)
        }
        return org.springframework.data.domain.PageImpl(
            sorted.map { mapAgency(it) },
            pageable,
            sorted.size.toLong()
        )
    }

    /**
     * Get detailed summary for a specific agency.
     * Cached with 24-hour TTL (T096).
     */
    @Cacheable(value = [RedisConfiguration.AGENCY_CACHE], key = "'summary_' + #agencyId.value")
    fun getAgencySummary(agencyId: AgencyId): AgencySummaryDTO? {
        val agency = agencyRepository.findById(agencyId).orElse(null) ?: return null
        val routes = routeRepository.findByAgency(agency, Pageable.unpaged()).toList()
        return AgencySummaryDTO(
            id = agency.agencyOnestopId.value,
            name = agency.name,
            routeCount = routes.size,
            averageHeadwayMinutes = null,
            minHeadwayMinutes = null,
            maxHeadwayMinutes = null
        )
    }

    private fun mapAgency(agency: com.mobilispect.backend.transitanalysis.domain.model.Agency): AgencyDTO {
        val routes = routeRepository.findByAgency(agency, Pageable.unpaged()).toList()
        val routesByType = routes.groupingBy { it.routeType }.eachCount()
        val regionIds = agency.feed.regions.map { it.regionOnestopId.value }.toSet()
        return AgencyDTO(
            id = agency.agencyOnestopId.value,
            name = agency.name,
            feedOnestopId = agency.feed.feedOnestopId.value,
            regionIds = regionIds,
            routeCount = routes.size,
            activeRouteCount = routes.count { it.active },
            routesByType = RouteType.values().associateWith { routesByType[it] ?: 0 }
        )
    }
}
