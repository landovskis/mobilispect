package com.mobilispect.backend.transitanalysis.domain.repository

import com.mobilispect.backend.agency.domain.model.ids.AgencyId
import com.mobilispect.backend.transitanalysis.domain.model.Route
import com.mobilispect.backend.transitanalysis.domain.model.RouteType
import com.mobilispect.backend.transitanalysis.domain.model.ids.RouteId
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import java.time.Instant

/**
 * Domain repository for [Route] entities.
 *
 * Provides data access methods for routes using domain models and type-safe value class IDs.
 * Implementation delegates to JPA repositories and uses mappers for domain/data conversion.
 */
interface RouteRepository {

    /**
     * Find a route by its ID.
     */
    fun findById(routeId: RouteId): Route?

    /**
     * Find all routes operated by a specific agency.
     */
    fun findByAgencyId(agencyId: AgencyId, pageable: Pageable): Page<Route>

    /**
     * Find all active routes operated by a specific agency.
     */
    fun findByAgencyIdAndActive(agencyId: AgencyId, pageable: Pageable): Page<Route>

    /**
     * Find routes by agency and route type.
     */
    fun findByAgencyIdAndRouteType(agencyId: AgencyId, routeType: RouteType, pageable: Pageable): Page<Route>

    /**
     * Find active routes by agency and route type.
     */
    fun findByAgencyIdAndRouteTypeAndActive(agencyId: AgencyId, routeType: RouteType, pageable: Pageable): Page<Route>

    /**
     * Find route by agency and GTFS route ID.
     */
    fun findByAgencyIdAndGtfsRouteId(agencyId: AgencyId, gtfsRouteId: String): Route?

    /**
     * Find all routes with a specific active status.
     */
    fun findByActive(active: Boolean, pageable: Pageable): Page<Route>

    /**
     * Find routes by route type across all agencies.
     */
    fun findByRouteType(routeType: RouteType, pageable: Pageable): Page<Route>

    /**
     * Find routes updated since a specific timestamp.
     */
    fun findByUpdatedAtAfter(since: Instant, pageable: Pageable): Page<Route>

    /**
     * Count routes for a specific agency.
     */
    fun countByAgencyId(agencyId: AgencyId): Long

    /**
     * Count active routes for a specific agency.
     */
    fun countActiveByAgencyId(agencyId: AgencyId): Long

    /**
     * Count routes of a specific type for an agency.
     */
    fun countByAgencyIdAndRouteType(agencyId: AgencyId, routeType: RouteType): Long

    /**
     * Check if a route exists for a specific agency and GTFS ID.
     */
    fun existsByAgencyIdAndGtfsRouteId(agencyId: AgencyId, gtfsRouteId: String): Boolean

    /**
     * Save a route.
     */
    fun save(route: Route): Route

    /**
     * Delete a route by ID.
     */
    fun deleteById(routeId: RouteId)

    /**
     * Find all routes.
     */
    fun findAll(): List<Route>
}
