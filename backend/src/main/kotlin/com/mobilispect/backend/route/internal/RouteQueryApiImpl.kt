package com.mobilispect.backend.route.internal

import com.mobilispect.backend.agency.domain.model.ids.AgencyId
import com.mobilispect.backend.route.api.RouteDTO
import com.mobilispect.backend.route.api.RouteQueryApi
import com.mobilispect.backend.route.domain.model.ids.RouteId
import com.mobilispect.backend.route.domain.repository.RouteRepository
import org.springframework.stereotype.Component

/**
 * Internal implementation of RouteQueryApi.
 *
 * Bridges the Route module API to the underlying repository layer.
 * This implementation is internal to the route module and should not be accessed directly by other modules.
 */
@Component
internal class RouteQueryApiImpl(
    private val routeRepository: RouteRepository
) : RouteQueryApi {

    override fun findRouteById(routeId: RouteId): RouteDTO? =
        routeRepository.findById(routeId)?.let { route ->
            RouteDTO(
                routeId = route.id,
                agencyId = route.agencyId,
                gtfsRouteId = route.gtfsRouteId,
                shortName = route.shortName,
                longName = route.longName,
                routeType = route.routeType,
                color = route.color,
                textColor = route.textColor,
                active = route.active
            )
        }

    override fun findRoutesByAgency(agencyId: AgencyId): List<RouteDTO> =
        routeRepository.findAll()
            .filter { it.agencyId == agencyId }
            .map { route ->
                RouteDTO(
                    routeId = route.id,
                    agencyId = route.agencyId,
                    gtfsRouteId = route.gtfsRouteId,
                    shortName = route.shortName,
                    longName = route.longName,
                    routeType = route.routeType,
                    color = route.color,
                    textColor = route.textColor,
                    active = route.active
                )
            }

    override fun validateRouteExists(routeId: RouteId): Boolean =
        routeRepository.findById(routeId) != null
}
