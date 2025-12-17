package com.mobilispect.backend.transitanalysis.domain.repository

import com.mobilispect.backend.transitanalysis.domain.model.Route
import com.mobilispect.backend.transitanalysis.domain.model.RouteVariant
import com.mobilispect.backend.transitanalysis.domain.model.ids.VariantHash
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import java.time.Instant

/**
 * Repository for [RouteVariant] entities in the transit analysis module.
 *
 * Provides data access methods for route variants, including queries by route,
 * direction, active status, and temporal windows (first/last seen). All query
 * methods support pagination for efficient data retrieval in large datasets.
 *
 * Route variants represent distinct stop patterns for a route. The variant ID
 * is a SHA-256 hash of the stop pattern for uniqueness verification.
 */
@Repository
interface RouteVariantRepository : JpaRepository<RouteVariant, VariantHash> {

    /**
     * Find all variants for a specific route by route ID.
     *
     * @param routeId The route ID to search within
     * @return List of variants belonging to the specified route
     */
    @Query("SELECT rv FROM RouteVariant rv JOIN rv.route r WHERE r.id = :routeId ORDER BY rv.stopCount ASC, rv.id ASC")
    fun findByRouteId(
        @Param("routeId") routeId: com.mobilispect.backend.transitanalysis.domain.model.ids.RouteId
    ): List<RouteVariant>

    /**
     * Find all variants for a specific route.
     *
     * @param route The route entity to search within
     * @param pageable Pagination parameters
     * @return Page of variants belonging to the specified route
     */
    @Query("SELECT rv FROM RouteVariant rv WHERE rv.route = :route ORDER BY rv.stopCount ASC, rv.id ASC")
    fun findByRoute(
        @Param("route") route: Route,
        pageable: Pageable
    ): Page<RouteVariant>

    /**
     * Find all active variants for a specific route.
     *
     * @param route The route entity to search within
     * @param pageable Pagination parameters
     * @return Page of active variants belonging to the specified route
     */
    @Query("SELECT rv FROM RouteVariant rv WHERE rv.route = :route AND rv.active = true ORDER BY rv.stopCount ASC, rv.id ASC")
    fun findByRouteAndActive(
        @Param("route") route: Route,
        pageable: Pageable
    ): Page<RouteVariant>

    /**
     * Find variants for a specific route and direction.
     *
     * Direction ID follows GTFS convention: 0 = outbound, 1 = inbound, null = unknown.
     *
     * @param route The route entity to search within
     * @param directionId The GTFS direction ID (0 or 1)
     * @param pageable Pagination parameters
     * @return Page of variants matching the specified route and direction
     */
    @Query("SELECT rv FROM RouteVariant rv WHERE rv.route = :route AND rv.directionId = :directionId ORDER BY rv.stopCount ASC, rv.id ASC")
    fun findByRouteAndDirectionId(
        @Param("route") route: Route,
        @Param("directionId") directionId: Int,
        pageable: Pageable
    ): Page<RouteVariant>

    /**
     * Find active variants for a specific route and direction.
     *
     * @param route The route entity to search within
     * @param directionId The GTFS direction ID (0 or 1)
     * @param pageable Pagination parameters
     * @return Page of active variants matching the specified route and direction
     */
    @Query("SELECT rv FROM RouteVariant rv WHERE rv.route = :route AND rv.directionId = :directionId AND rv.active = true ORDER BY rv.stopCount ASC, rv.id ASC")
    fun findByRouteAndDirectionIdAndActive(
        @Param("route") route: Route,
        @Param("directionId") directionId: Int,
        pageable: Pageable
    ): Page<RouteVariant>

    /**
     * Find variants with a specific number of stops.
     *
     * Useful for finding long or short variants within a route.
     *
     * @param stopCount The exact number of stops to filter by
     * @param pageable Pagination parameters
     * @return Page of variants with the specified stop count
     */
    @Query("SELECT rv FROM RouteVariant rv JOIN rv.route r WHERE rv.stopCount = :stopCount ORDER BY r.id ASC, rv.id ASC")
    fun findByStopCount(
        @Param("stopCount") stopCount: Int,
        pageable: Pageable
    ): Page<RouteVariant>

    /**
     * Find all variants with a specific active status.
     *
     * @param active Whether to search for active or inactive variants
     * @param pageable Pagination parameters
     * @return Page of variants with the specified active status
     */
    @Query("SELECT rv FROM RouteVariant rv JOIN rv.route r WHERE rv.active = :active ORDER BY r.id ASC, rv.stopCount ASC")
    fun findByActive(
        @Param("active") active: Boolean,
        pageable: Pageable
    ): Page<RouteVariant>

    /**
     * Find variants first observed in a time window.
     *
     * Useful for tracking newly added variants.
     *
     * @param after Instant to search from (inclusive)
     * @param before Instant to search until (inclusive)
     * @param pageable Pagination parameters
     * @return Page of variants first seen in the specified time window
     */
    @Query("SELECT rv FROM RouteVariant rv WHERE rv.firstSeen >= :after AND rv.firstSeen <= :before ORDER BY rv.firstSeen DESC")
    fun findByFirstSeenBetween(
        @Param("after") after: Instant,
        @Param("before") before: Instant,
        pageable: Pageable
    ): Page<RouteVariant>

    /**
     * Find variants last observed after a specific timestamp.
     *
     * Useful for finding recently active variants.
     *
     * @param since Instant timestamp to filter by (inclusive)
     * @param pageable Pagination parameters
     * @return Page of variants last seen after the specified time
     */
    @Query("SELECT rv FROM RouteVariant rv WHERE rv.lastSeen >= :since ORDER BY rv.lastSeen DESC")
    fun findByLastSeenAfter(
        @Param("since") since: Instant,
        pageable: Pageable
    ): Page<RouteVariant>

    /**
     * Find variants by first and last stop IDs.
     *
     * Useful for identifying variants that serve specific origin-destination pairs.
     *
     * @param firstStopId The ID of the first stop
     * @param lastStopId The ID of the last stop
     * @param pageable Pagination parameters
     * @return Page of variants with the specified first and last stops
     */
    @Query("SELECT rv FROM RouteVariant rv JOIN rv.route r WHERE rv.firstStopId = :firstStopId AND rv.lastStopId = :lastStopId ORDER BY r.id ASC")
    fun findByFirstStopIdAndLastStopId(
        @Param("firstStopId") firstStopId: String,
        @Param("lastStopId") lastStopId: String,
        pageable: Pageable
    ): Page<RouteVariant>

    /**
     * Count variants for a specific route.
     *
     * @param route The route entity to count within
     * @return Number of variants for the specified route
     */
    @Query("SELECT COUNT(rv) FROM RouteVariant rv WHERE rv.route = :route")
    fun countByRoute(@Param("route") route: Route): Long

    /**
     * Count active variants for a specific route.
     *
     * @param route The route entity to count within
     * @return Number of active variants for the specified route
     */
    @Query("SELECT COUNT(rv) FROM RouteVariant rv WHERE rv.route = :route AND rv.active = true")
    fun countActiveByRoute(@Param("route") route: Route): Long

    /**
     * Count variants by direction for a specific route.
     *
     * @param route The route entity to count within
     * @param directionId The GTFS direction ID
     * @return Number of variants with the specified direction
     */
    @Query("SELECT COUNT(rv) FROM RouteVariant rv WHERE rv.route = :route AND rv.directionId = :directionId")
    fun countByRouteAndDirectionId(
        @Param("route") route: Route,
        @Param("directionId") directionId: Int
    ): Long

    /**
     * Check if a variant with specific first and last stops exists for a route.
     *
     * @param route The route entity to search within
     * @param firstStopId The ID of the first stop
     * @param lastStopId The ID of the last stop
     * @return true if such a variant exists, false otherwise
     */
    @Query("SELECT CASE WHEN COUNT(rv) > 0 THEN true ELSE false END FROM RouteVariant rv WHERE rv.route = :route AND rv.firstStopId = :firstStopId AND rv.lastStopId = :lastStopId")
    fun existsByRouteAndFirstStopIdAndLastStopId(
        @Param("route") route: Route,
        @Param("firstStopId") firstStopId: String,
        @Param("lastStopId") lastStopId: String
    ): Boolean
}
