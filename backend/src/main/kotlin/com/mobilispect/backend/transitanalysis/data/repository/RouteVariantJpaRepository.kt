package com.mobilispect.backend.transitanalysis.data.repository

import com.mobilispect.backend.transitanalysis.data.entity.RouteEntity
import com.mobilispect.backend.transitanalysis.data.entity.RouteVariantEntity
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.time.Instant

/**
 * JPA repository for [RouteVariantEntity] data layer.
 *
 * Provides data access for route variant entities using plain String IDs.
 * Used by RouteVariantRepository implementation to persist and retrieve domain models.
 */
interface RouteVariantJpaRepository : JpaRepository<RouteVariantEntity, String> {

    /**
     * Find all variants for a specific route by route ID.
     */
    @Query("SELECT rv FROM RouteVariantEntity rv WHERE rv.route.id = :routeId ORDER BY rv.stopCount ASC, rv.id ASC")
    fun findByRouteId(@Param("routeId") routeId: String): List<RouteVariantEntity>

    /**
     * Find all variants for a specific route.
     */
    @Query("SELECT rv FROM RouteVariantEntity rv WHERE rv.route = :route ORDER BY rv.stopCount ASC, rv.id ASC")
    fun findByRoute(
        @Param("route") route: RouteEntity,
        pageable: Pageable
    ): Page<RouteVariantEntity>

    /**
     * Find all active variants for a specific route.
     */
    @Query("SELECT rv FROM RouteVariantEntity rv WHERE rv.route = :route AND rv.active = true ORDER BY rv.stopCount ASC, rv.id ASC")
    fun findByRouteAndActive(
        @Param("route") route: RouteEntity,
        pageable: Pageable
    ): Page<RouteVariantEntity>

    /**
     * Find variants for a specific route and direction.
     */
    @Query("SELECT rv FROM RouteVariantEntity rv WHERE rv.route = :route AND rv.directionId = :directionId ORDER BY rv.stopCount ASC, rv.id ASC")
    fun findByRouteAndDirectionId(
        @Param("route") route: RouteEntity,
        @Param("directionId") directionId: Int,
        pageable: Pageable
    ): Page<RouteVariantEntity>

    /**
     * Find active variants for a specific route and direction.
     */
    @Query("SELECT rv FROM RouteVariantEntity rv WHERE rv.route = :route AND rv.directionId = :directionId AND rv.active = true ORDER BY rv.stopCount ASC, rv.id ASC")
    fun findByRouteAndDirectionIdAndActive(
        @Param("route") route: RouteEntity,
        @Param("directionId") directionId: Int,
        pageable: Pageable
    ): Page<RouteVariantEntity>

    /**
     * Find variants with a specific number of stops.
     */
    @Query("SELECT rv FROM RouteVariantEntity rv JOIN rv.route r WHERE rv.stopCount = :stopCount ORDER BY r.id ASC, rv.id ASC")
    fun findByStopCount(
        @Param("stopCount") stopCount: Int,
        pageable: Pageable
    ): Page<RouteVariantEntity>

    /**
     * Find all variants with a specific active status.
     */
    @Query("SELECT rv FROM RouteVariantEntity rv JOIN rv.route r WHERE rv.active = :active ORDER BY r.id ASC, rv.stopCount ASC")
    fun findByActive(
        @Param("active") active: Boolean,
        pageable: Pageable
    ): Page<RouteVariantEntity>

    /**
     * Find variants first observed in a time window.
     */
    @Query("SELECT rv FROM RouteVariantEntity rv WHERE rv.firstSeen >= :after AND rv.firstSeen <= :before ORDER BY rv.firstSeen DESC")
    fun findByFirstSeenBetween(
        @Param("after") after: Instant,
        @Param("before") before: Instant,
        pageable: Pageable
    ): Page<RouteVariantEntity>

    /**
     * Find variants last observed after a specific timestamp.
     */
    @Query("SELECT rv FROM RouteVariantEntity rv WHERE rv.lastSeen >= :since ORDER BY rv.lastSeen DESC")
    fun findByLastSeenAfter(
        @Param("since") since: Instant,
        pageable: Pageable
    ): Page<RouteVariantEntity>

    /**
     * Find variants by first and last stop IDs.
     */
    @Query("SELECT rv FROM RouteVariantEntity rv JOIN rv.route r WHERE rv.firstStopId = :firstStopId AND rv.lastStopId = :lastStopId ORDER BY r.id ASC")
    fun findByFirstStopIdAndLastStopId(
        @Param("firstStopId") firstStopId: String,
        @Param("lastStopId") lastStopId: String,
        pageable: Pageable
    ): Page<RouteVariantEntity>

    /**
     * Count variants for a specific route.
     */
    @Query("SELECT COUNT(rv) FROM RouteVariantEntity rv WHERE rv.route = :route")
    fun countByRoute(@Param("route") route: RouteEntity): Long

    /**
     * Count active variants for a specific route.
     */
    @Query("SELECT COUNT(rv) FROM RouteVariantEntity rv WHERE rv.route = :route AND rv.active = true")
    fun countActiveByRoute(@Param("route") route: RouteEntity): Long

    /**
     * Count variants by direction for a specific route.
     */
    @Query("SELECT COUNT(rv) FROM RouteVariantEntity rv WHERE rv.route = :route AND rv.directionId = :directionId")
    fun countByRouteAndDirectionId(
        @Param("route") route: RouteEntity,
        @Param("directionId") directionId: Int
    ): Long

    /**
     * Check if a variant with specific first and last stops exists for a route.
     */
    @Query("SELECT CASE WHEN COUNT(rv) > 0 THEN true ELSE false END FROM RouteVariantEntity rv WHERE rv.route.id = :routeId AND rv.firstStopId = :firstStopId AND rv.lastStopId = :lastStopId")
    fun existsByRouteIdAndFirstStopIdAndLastStopId(
        @Param("routeId") routeId: String,
        @Param("firstStopId") firstStopId: String,
        @Param("lastStopId") lastStopId: String
    ): Boolean
}
