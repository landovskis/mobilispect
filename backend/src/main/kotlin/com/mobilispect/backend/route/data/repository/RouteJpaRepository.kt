package com.mobilispect.backend.route.data.repository

import com.mobilispect.backend.route.data.entity.RouteEntity
import java.time.Instant
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

/**
 * JPA repository for [RouteEntity] data layer.
 *
 * Provides data access for route entities using plain String IDs. Used by RouteRepository
 * implementation to persist and retrieve domain models.
 */
interface RouteJpaRepository : JpaRepository<RouteEntity, String> {

  /** Find all routes operated by a specific agency. */
  @Query(
    "SELECT r FROM RouteEntity r WHERE r.agencyId = :agencyId ORDER BY r.shortName ASC, r.longName ASC"
  )
  fun findByAgencyId(@Param("agencyId") agencyId: String, pageable: Pageable): Page<RouteEntity>

  /** Find all active routes operated by a specific agency. */
  @Query(
    "SELECT r FROM RouteEntity r WHERE r.agencyId = :agencyId AND r.active = true ORDER BY r.shortName ASC, r.longName ASC"
  )
  fun findByAgencyIdAndActive(
    @Param("agencyId") agencyId: String,
    pageable: Pageable,
  ): Page<RouteEntity>

  /** Find routes by agency and route type. */
  @Query(
    "SELECT r FROM RouteEntity r WHERE r.agencyId = :agencyId AND r.routeType = :routeType ORDER BY r.shortName ASC, r.longName ASC"
  )
  fun findByAgencyIdAndRouteType(
    @Param("agencyId") agencyId: String,
    @Param("routeType") routeType: String,
    pageable: Pageable,
  ): Page<RouteEntity>

  /** Find active routes by agency and route type. */
  @Query(
    "SELECT r FROM RouteEntity r WHERE r.agencyId = :agencyId AND r.routeType = :routeType AND r.active = true ORDER BY r.shortName ASC, r.longName ASC"
  )
  fun findByAgencyIdAndRouteTypeAndActive(
    @Param("agencyId") agencyId: String,
    @Param("routeType") routeType: String,
    pageable: Pageable,
  ): Page<RouteEntity>

  /** Find route by agency ID and GTFS route ID. */
  @Query(
    "SELECT r FROM RouteEntity r WHERE r.agencyId = :agencyId AND r.gtfsRouteId = :gtfsRouteId"
  )
  fun findByAgencyIdAndGtfsRouteId(
    @Param("agencyId") agencyId: String,
    @Param("gtfsRouteId") gtfsRouteId: String,
  ): RouteEntity?

  /** Find all routes with a specific active status. */
  @Query(
    "SELECT r FROM RouteEntity r WHERE r.active = :active ORDER BY r.shortName ASC, r.longName ASC"
  )
  fun findByActive(@Param("active") active: Boolean, pageable: Pageable): Page<RouteEntity>

  /** Find routes by route type across all agencies. */
  @Query(
    "SELECT r FROM RouteEntity r WHERE r.routeType = :routeType ORDER BY r.shortName ASC, r.longName ASC"
  )
  fun findByRouteType(@Param("routeType") routeType: String, pageable: Pageable): Page<RouteEntity>

  /** Find routes updated since a specific timestamp. */
  @Query("SELECT r FROM RouteEntity r WHERE r.updatedAt >= :since ORDER BY r.updatedAt DESC")
  fun findByUpdatedAtAfter(@Param("since") since: Instant, pageable: Pageable): Page<RouteEntity>

  /** Count routes for a specific agency. */
  @Query("SELECT COUNT(r) FROM RouteEntity r WHERE r.agencyId = :agencyId")
  fun countByAgencyId(@Param("agencyId") agencyId: String): Long

  /** Count active routes for a specific agency. */
  @Query("SELECT COUNT(r) FROM RouteEntity r WHERE r.agencyId = :agencyId AND r.active = true")
  fun countActiveByAgencyId(@Param("agencyId") agencyId: String): Long

  /** Count routes of a specific type for an agency. */
  @Query(
    "SELECT COUNT(r) FROM RouteEntity r WHERE r.agencyId = :agencyId AND r.routeType = :routeType"
  )
  fun countByAgencyIdAndRouteType(
    @Param("agencyId") agencyId: String,
    @Param("routeType") routeType: String,
  ): Long

  /** Check if a route exists for a specific agency and GTFS ID. */
  @Query(
    "SELECT CASE WHEN COUNT(r) > 0 THEN true ELSE false END FROM RouteEntity r WHERE r.agencyId = :agencyId AND r.gtfsRouteId = :gtfsRouteId"
  )
  fun existsByAgencyIdAndGtfsRouteId(
    @Param("agencyId") agencyId: String,
    @Param("gtfsRouteId") gtfsRouteId: String,
  ): Boolean
}
