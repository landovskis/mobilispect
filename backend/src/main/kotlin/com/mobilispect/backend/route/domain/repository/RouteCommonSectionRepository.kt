package com.mobilispect.backend.route.domain.repository

import com.mobilispect.backend.route.RouteId
import com.mobilispect.backend.route.domain.model.RouteCommonSection

/**
 * Domain repository for [RouteCommonSection] entities.
 *
 * Provides data access methods for route common sections using domain models.
 * A route common section represents the longest continuous sequence of stops
 * shared by ALL variants in a given direction.
 */
interface RouteCommonSectionRepository {

  /** Find common section by its ID. */
  fun findById(id: String): RouteCommonSection?

  /** Find common section for a route and direction. */
  fun findByRouteIdAndDirectionId(routeId: RouteId, directionId: Int?): RouteCommonSection?

  /** Find all common sections for a specific route. */
  fun findByRouteId(routeId: RouteId): List<RouteCommonSection>

  /** Save a route common section. */
  fun save(section: RouteCommonSection): RouteCommonSection

  /** Delete common section by ID. */
  fun deleteById(id: String)

  /** Delete all common sections for a route. */
  fun deleteByRouteId(routeId: RouteId)
}
