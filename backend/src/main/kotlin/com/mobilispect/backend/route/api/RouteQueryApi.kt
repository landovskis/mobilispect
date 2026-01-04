package com.mobilispect.backend.route.api

import com.mobilispect.backend.agency.AgencyId
import com.mobilispect.backend.route.domain.model.ids.RouteId

/**
 * Public API for querying routes.
 *
 * This is the Route module's API contract for cross-module communication. Other modules should use
 * this API instead of accessing repositories directly.
 *
 * Constitutional Requirement (Modular Monolith Ownership):
 * - No cross-module database access
 * - Communication via ports/events only
 */
interface RouteQueryApi {
  /**
   * Find a route by its ID.
   *
   * @param routeId The unique identifier for the route
   * @return The route DTO if found, null otherwise
   */
  fun findRouteById(routeId: RouteId): RouteDTO?

  /**
   * Find all routes associated with a specific agency.
   *
   * @param agencyId The agency identifier
   * @return List of routes for the agency
   */
  fun findRoutesByAgency(agencyId: AgencyId): List<RouteDTO>

  /**
   * Validate that a route exists.
   *
   * @param routeId The route identifier to validate
   * @return true if route exists, false otherwise
   */
  fun validateRouteExists(routeId: RouteId): Boolean
}
