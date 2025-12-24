package com.mobilispect.backend.transitanalysis.data.repository

import com.mobilispect.backend.agency.data.entity.AgencyEntity
import com.mobilispect.backend.transitanalysis.data.entity.RouteEntity
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.time.Instant

/**
 * JPA repository for [RouteEntity] data layer.
 *
 * Provides data access for route entities using plain String IDs.
 * Used by RouteRepository implementation to persist and retrieve domain models.
 */
interface RouteJpaRepository : JpaRepository<RouteEntity, String> {

    /**
     * Find all routes operated by a specific agency.
     */
    @Query("SELECT r FROM RouteEntity r WHERE r.agency = :agency ORDER BY r.shortName ASC, r.longName ASC")
    fun findByAgency(
        @Param("agency") agency: AgencyEntity,
        pageable: Pageable
    ): Page<RouteEntity>

    /**
     * Find all active routes operated by a specific agency.
     */
    @Query("SELECT r FROM RouteEntity r WHERE r.agency = :agency AND r.active = true ORDER BY r.shortName ASC, r.longName ASC")
    fun findByAgencyAndActive(
        @Param("agency") agency: AgencyEntity,
        pageable: Pageable
    ): Page<RouteEntity>

    /**
     * Find routes by agency and route type.
     */
    @Query("SELECT r FROM RouteEntity r WHERE r.agency = :agency AND r.routeType = :routeType ORDER BY r.shortName ASC, r.longName ASC")
    fun findByAgencyAndRouteType(
        @Param("agency") agency: AgencyEntity,
        @Param("routeType") routeType: String,
        pageable: Pageable
    ): Page<RouteEntity>

    /**
     * Find active routes by agency and route type.
     */
    @Query("SELECT r FROM RouteEntity r WHERE r.agency = :agency AND r.routeType = :routeType AND r.active = true ORDER BY r.shortName ASC, r.longName ASC")
    fun findByAgencyAndRouteTypeAndActive(
        @Param("agency") agency: AgencyEntity,
        @Param("routeType") routeType: String,
        pageable: Pageable
    ): Page<RouteEntity>

    /**
     * Find route by agency ID and GTFS route ID.
     */
    @Query("SELECT r FROM RouteEntity r WHERE r.agency.agencyOnestopId = :agencyId AND r.gtfsRouteId = :gtfsRouteId")
    fun findByAgencyIdAndGtfsRouteId(
        @Param("agencyId") agencyId: String,
        @Param("gtfsRouteId") gtfsRouteId: String
    ): RouteEntity?

    /**
     * Find all routes with a specific active status.
     */
    @Query("SELECT r FROM RouteEntity r WHERE r.active = :active ORDER BY r.agency.name ASC, r.shortName ASC")
    fun findByActive(
        @Param("active") active: Boolean,
        pageable: Pageable
    ): Page<RouteEntity>

    /**
     * Find routes by route type across all agencies.
     */
    @Query("SELECT r FROM RouteEntity r WHERE r.routeType = :routeType ORDER BY r.agency.name ASC, r.shortName ASC")
    fun findByRouteType(
        @Param("routeType") routeType: String,
        pageable: Pageable
    ): Page<RouteEntity>

    /**
     * Find routes updated since a specific timestamp.
     */
    @Query("SELECT r FROM RouteEntity r WHERE r.updatedAt >= :since ORDER BY r.updatedAt DESC")
    fun findByUpdatedAtAfter(
        @Param("since") since: Instant,
        pageable: Pageable
    ): Page<RouteEntity>

    /**
     * Count routes for a specific agency.
     */
    @Query("SELECT COUNT(r) FROM RouteEntity r WHERE r.agency = :agency")
    fun countByAgency(@Param("agency") agency: AgencyEntity): Long

    /**
     * Count active routes for a specific agency.
     */
    @Query("SELECT COUNT(r) FROM RouteEntity r WHERE r.agency = :agency AND r.active = true")
    fun countActiveByAgency(@Param("agency") agency: AgencyEntity): Long

    /**
     * Count routes of a specific type for an agency.
     */
    @Query("SELECT COUNT(r) FROM RouteEntity r WHERE r.agency = :agency AND r.routeType = :routeType")
    fun countByAgencyAndRouteType(
        @Param("agency") agency: AgencyEntity,
        @Param("routeType") routeType: String
    ): Long

    /**
     * Check if a route exists for a specific agency and GTFS ID.
     */
    @Query("SELECT CASE WHEN COUNT(r) > 0 THEN true ELSE false END FROM RouteEntity r WHERE r.agency.agencyOnestopId = :agencyId AND r.gtfsRouteId = :gtfsRouteId")
    fun existsByAgencyIdAndGtfsRouteId(
        @Param("agencyId") agencyId: String,
        @Param("gtfsRouteId") gtfsRouteId: String
    ): Boolean
}
