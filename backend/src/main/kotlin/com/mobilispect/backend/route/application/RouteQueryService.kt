package com.mobilispect.backend.route.application

import com.mobilispect.backend.agency.AgencyId
import com.mobilispect.backend.agency.domain.repository.AgencyRepository
import com.mobilispect.backend.route.api.dto.RouteDTO
import com.mobilispect.backend.route.domain.repository.RouteRepository
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Service

/** Query service for route-related operations. */
@Service
class RouteQueryService(
  private val routeRepository: RouteRepository,
  private val agencyRepository: AgencyRepository,
) {

  /** Get all routes for a specific agency with pagination. */
  fun getRoutesByAgency(agencyId: AgencyId, pageable: Pageable): Page<RouteDTO> {
    agencyRepository.findById(agencyId)
      ?: throw IllegalArgumentException("Agency not found: $agencyId")
    val routes = routeRepository.findByAgencyId(agencyId, pageable)

    return routes.map { route ->
      RouteDTO(
        id = route.id.value,
        agencyId = route.agencyId.value,
        shortName = route.shortName,
        longName = route.longName,
        routeType = route.routeType,
        active = route.active,
      )
    }
  }
}
