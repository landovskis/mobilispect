package com.mobilispect.backend.transitanalysis.domain.repository

import com.mobilispect.backend.transitanalysis.domain.model.Agency
import com.mobilispect.backend.transitanalysis.domain.model.Route
import com.mobilispect.backend.transitanalysis.domain.model.RouteType
import com.mobilispect.backend.transitanalysis.domain.model.ids.RouteId
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import java.time.Instant

/**
 * Repository for [Route] entities in the transit analysis module.
 *
 * Provides data access methods for routes, including queries by agency, route type,
 * active status, and timestamps. All query methods support pagination for efficient
 * data retrieval in large datasets.
 */
@Repository
interface RouteRepository : JpaRepository<Route, RouteId> {

    /**
     * Find all routes operated by a specific agency.
     *
     * @param agency The agency entity to search within
     * @param pageable Pagination parameters
     * @return Page of routes operated by the agency
     */
    @Query("SELECT r FROM Route r WHERE r.agency = :agency ORDER BY r.shortName ASC, r.longName ASC")
    fun findByAgency(
        @Param("agency") agency: Agency,
        pageable: Pageable
    ): Page<Route>

    /**
     * Find all active routes operated by a specific agency.
     *
     * @param agency The agency entity to search within
     * @param pageable Pagination parameters
     * @return Page of active routes operated by the agency
     */
    @Query("SELECT r FROM Route r WHERE r.agency = :agency AND r.active = true ORDER BY r.shortName ASC, r.longName ASC")
    fun findByAgencyAndActive(
        @Param("agency") agency: Agency,
        pageable: Pageable
    ): Page<Route>

    /**
     * Find routes by agency and route type.
     *
     * Useful for filtering routes by transit mode (bus, rail, subway, etc.).
     *
     * @param agency The agency entity to search within
     * @param routeType The GTFS route type to filter by
     * @param pageable Pagination parameters
     * @return Page of routes matching the specified agency and type
     */
    @Query("SELECT r FROM Route r WHERE r.agency = :agency AND r.routeType = :routeType ORDER BY r.shortName ASC, r.longName ASC")
    fun findByAgencyAndRouteType(
        @Param("agency") agency: Agency,
        @Param("routeType") routeType: RouteType,
        pageable: Pageable
    ): Page<Route>

    /**
     * Find active routes by agency and route type.
     *
     * @param agency The agency entity to search within
     * @param routeType The GTFS route type to filter by
     * @param pageable Pagination parameters
     * @return Page of active routes matching the specified agency and type
     */
    @Query("SELECT r FROM Route r WHERE r.agency = :agency AND r.routeType = :routeType AND r.active = true ORDER BY r.shortName ASC, r.longName ASC")
    fun findByAgencyAndRouteTypeAndActive(
        @Param("agency") agency: Agency,
        @Param("routeType") routeType: RouteType,
        pageable: Pageable
    ): Page<Route>

    /**
     * Find route by agency and GTFS route ID.
     *
     * GTFS route ID is the identifier used in the GTFS feed's routes.txt file.
     *
     * @param agency The agency entity to search within
     * @param gtfsRouteId The GTFS route ID from routes.txt
     * @return Route if found, empty Optional otherwise
     */
    @Query("SELECT r FROM Route r WHERE r.agency = :agency AND r.gtfsRouteId = :gtfsRouteId")
    fun findByAgencyAndGtfsRouteId(
        @Param("agency") agency: Agency,
        @Param("gtfsRouteId") gtfsRouteId: String
    ): java.util.Optional<Route>

    /**
     * Find all routes with a specific active status.
     *
     * @param active Whether to search for active or inactive routes
     * @param pageable Pagination parameters
     * @return Page of routes with the specified active status
     */
    @Query("SELECT r FROM Route r WHERE r.active = :active ORDER BY r.agency.name ASC, r.shortName ASC")
    fun findByActive(
        @Param("active") active: Boolean,
        pageable: Pageable
    ): Page<Route>

    /**
     * Find routes by route type across all agencies.
     *
     * @param routeType The GTFS route type to filter by
     * @param pageable Pagination parameters
     * @return Page of routes with the specified type
     */
    @Query("SELECT r FROM Route r WHERE r.routeType = :routeType ORDER BY r.agency.name ASC, r.shortName ASC")
    fun findByRouteType(
        @Param("routeType") routeType: RouteType,
        pageable: Pageable
    ): Page<Route>

    /**
     * Find routes updated since a specific timestamp.
     *
     * Useful for incremental imports and change detection.
     *
     * @param since Instant timestamp to filter by
     * @param pageable Pagination parameters
     * @return Page of routes updated since the specified time
     */
    @Query("SELECT r FROM Route r WHERE r.updatedAt >= :since ORDER BY r.updatedAt DESC")
    fun findByUpdatedAtAfter(
        @Param("since") since: Instant,
        pageable: Pageable
    ): Page<Route>

    /**
     * Count routes for a specific agency.
     *
     * @param agency The agency entity to count within
     * @return Number of routes operated by the agency
     */
    @Query("SELECT COUNT(r) FROM Route r WHERE r.agency = :agency")
    fun countByAgency(@Param("agency") agency: Agency): Long

    /**
     * Count active routes for a specific agency.
     *
     * @param agency The agency entity to count within
     * @return Number of active routes operated by the agency
     */
    @Query("SELECT COUNT(r) FROM Route r WHERE r.agency = :agency AND r.active = true")
    fun countActiveByAgency(@Param("agency") agency: Agency): Long

    /**
     * Count routes of a specific type for an agency.
     *
     * @param agency The agency entity to count within
     * @param routeType The GTFS route type to filter by
     * @return Number of routes of the specified type
     */
    @Query("SELECT COUNT(r) FROM Route r WHERE r.agency = :agency AND r.routeType = :routeType")
    fun countByAgencyAndRouteType(
        @Param("agency") agency: Agency,
        @Param("routeType") routeType: RouteType
    ): Long

    /**
     * Check if a route exists for a specific agency and GTFS ID.
     *
     * @param agency The agency entity to search within
     * @param gtfsRouteId The GTFS route ID
     * @return true if the route exists, false otherwise
     */
    @Query("SELECT CASE WHEN COUNT(r) > 0 THEN true ELSE false END FROM Route r WHERE r.agency = :agency AND r.gtfsRouteId = :gtfsRouteId")
    fun existsByAgencyAndGtfsRouteId(
        @Param("agency") agency: Agency,
        @Param("gtfsRouteId") gtfsRouteId: String
    ): Boolean
}
