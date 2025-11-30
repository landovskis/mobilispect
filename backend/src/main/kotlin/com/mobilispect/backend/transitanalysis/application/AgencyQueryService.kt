package com.mobilispect.backend.transitanalysis.application

import com.mobilispect.backend.transitanalysis.api.dto.AgencyDTO
import com.mobilispect.backend.transitanalysis.api.dto.AgencySummaryDTO
import com.mobilispect.backend.transitanalysis.domain.model.RouteType
import com.mobilispect.backend.transitanalysis.domain.model.ids.AgencyId
import com.mobilispect.backend.transitanalysis.domain.repository.AgencyRepository
import com.mobilispect.backend.transitanalysis.domain.repository.RouteRepository
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Service

@Service
class AgencyQueryService(
    private val agencyRepository: AgencyRepository,
    private val routeRepository: RouteRepository
) {
    fun getAgencies(pageable: Pageable): Page<AgencyDTO> {
        val agencies = agencyRepository.findAll(pageable)
        return agencies.map { agency ->
            val routes = routeRepository.findByAgency(agency, Pageable.unpaged()).toList()
            val routesByType = routes.groupingBy { it.routeType }.eachCount()
            AgencyDTO(
                id = agency.agencyOnestopId.value,
                name = agency.name,
                feedOnestopId = agency.feed.feedOnestopId.value,
                regionIds = emptySet(), // region membership via feed_regions is not exposed yet
                routeCount = routes.size,
                activeRouteCount = routes.count { it.active },
                routesByType = RouteType.values().associateWith { routesByType[it] ?: 0 }
            )
        }
    }

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
}
