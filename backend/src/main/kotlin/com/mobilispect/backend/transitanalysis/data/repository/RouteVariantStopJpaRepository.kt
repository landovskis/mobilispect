package com.mobilispect.backend.transitanalysis.data.repository

import com.mobilispect.backend.transitanalysis.data.entity.RouteVariantStopEntity
import com.mobilispect.backend.transitanalysis.data.entity.RouteVariantStopId
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

/**
 * JPA repository for [RouteVariantStopEntity] junction table.
 *
 * Provides data access for route_variant_stops junction table linking variants to their ordered stops.
 * Enables queries like "find all variants serving stop X" and "find all stops for variant Y".
 */
interface RouteVariantStopJpaRepository : JpaRepository<RouteVariantStopEntity, RouteVariantStopId> {

    /**
     * Find all stops for a specific route variant, ordered by sequence.
     */
    @Query("SELECT rvs FROM RouteVariantStopEntity rvs WHERE rvs.id.variantId = :variantId ORDER BY rvs.id.stopSequence ASC")
    fun findByVariantIdOrderBySequence(@Param("variantId") variantId: String): List<RouteVariantStopEntity>

    /**
     * Find all route variants serving a specific stop.
     */
    @Query("SELECT rvs FROM RouteVariantStopEntity rvs WHERE rvs.stop.stopOnestopId = :stopOnestopId")
    fun findByStopOnestopId(@Param("stopOnestopId") stopOnestopId: String): List<RouteVariantStopEntity>

    /**
     * Delete all stop references for a specific route variant.
     * Used when updating a variant's stop pattern.
     */
    @Modifying
    @Query("DELETE FROM RouteVariantStopEntity rvs WHERE rvs.id.variantId = :variantId")
    fun deleteByVariantId(@Param("variantId") variantId: String)

    /**
     * Count stops for a specific route variant.
     */
    @Query("SELECT COUNT(rvs) FROM RouteVariantStopEntity rvs WHERE rvs.id.variantId = :variantId")
    fun countByVariantId(@Param("variantId") variantId: String): Long

    /**
     * Count route variants serving a specific stop.
     */
    @Query("SELECT COUNT(rvs) FROM RouteVariantStopEntity rvs WHERE rvs.stop.stopOnestopId = :stopOnestopId")
    fun countByStopOnestopId(@Param("stopOnestopId") stopOnestopId: String): Long
}
