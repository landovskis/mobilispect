package com.mobilispect.backend.route.domain.repository

import com.mobilispect.backend.route.RouteId
import com.mobilispect.backend.route.domain.model.RouteVariant
import com.mobilispect.backend.route.domain.model.ids.VariantHash
import java.time.Instant
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable

/**
 * Domain repository for [RouteVariant] entities.
 *
 * Provides data access methods for route variants using domain models and type-safe value class
 * IDs. Implementation delegates to JPA repositories and uses mappers for domain/data conversion.
 */
interface RouteVariantRepository {

  /** Find variant by its ID (SHA-256 hash). */
  fun findById(id: VariantHash): RouteVariant?

  /** Find all variants for a specific route by route ID. */
  fun findByRouteId(routeId: RouteId): List<RouteVariant>

  /** Find all variants for a specific route. */
  fun findByRouteId(routeId: RouteId, pageable: Pageable): Page<RouteVariant>

  /** Find all active variants for a specific route. */
  fun findByRouteIdAndActive(routeId: RouteId, pageable: Pageable): Page<RouteVariant>

  /** Find variants for a specific route and direction. */
  fun findByRouteIdAndDirectionId(
    routeId: RouteId,
    directionId: Int,
    pageable: Pageable,
  ): Page<RouteVariant>

  /** Find active variants for a specific route and direction. */
  fun findByRouteIdAndDirectionIdAndActive(
    routeId: RouteId,
    directionId: Int,
    pageable: Pageable,
  ): Page<RouteVariant>

  /** Find variants with a specific number of stops. */
  fun findByStopCount(stopCount: Int, pageable: Pageable): Page<RouteVariant>

  /** Find all variants with a specific active status. */
  fun findByActive(active: Boolean, pageable: Pageable): Page<RouteVariant>

  /** Find variants first observed in a time window. */
  fun findByFirstSeenBetween(
    after: Instant,
    before: Instant,
    pageable: Pageable,
  ): Page<RouteVariant>

  /** Find variants last observed after a specific timestamp. */
  fun findByLastSeenAfter(since: Instant, pageable: Pageable): Page<RouteVariant>

  /** Find variants by first and last stop IDs. */
  fun findByFirstStopIdAndLastStopId(
    firstStopId: String,
    lastStopId: String,
    pageable: Pageable,
  ): Page<RouteVariant>

  /** Count variants for a specific route. */
  fun countByRouteId(routeId: RouteId): Long

  /** Count active variants for a specific route. */
  fun countActiveByRouteId(routeId: RouteId): Long

  /** Count variants by direction for a specific route. */
  fun countByRouteIdAndDirectionId(routeId: RouteId, directionId: Int): Long

  /** Check if a variant with specific first and last stops exists for a route. */
  fun existsByRouteIdAndFirstStopIdAndLastStopId(
    routeId: RouteId,
    firstStopId: String,
    lastStopId: String,
  ): Boolean

  /** Save a route variant. */
  fun save(variant: RouteVariant): RouteVariant

  /** Delete a variant by ID. */
  fun deleteById(id: VariantHash)

  /** Find all variants. */
  fun findAll(): List<RouteVariant>
}
