package com.mobilispect.backend.route.domain.repository

import com.mobilispect.backend.route.domain.model.StopSpacing
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import java.util.UUID

/**
 * Repository for [StopSpacing] entities.
 *
 * Provides data access methods for stop spacing data between consecutive stops
 * on route variants.
 */
@Repository
interface StopSpacingRepository : JpaRepository<StopSpacing, UUID> {

    /**
     * Find all stop spacing records for a specific route variant, ordered by sequence.
     *
     * @param variantId The route variant ID to search for
     * @return List of stop spacing records ordered by stop sequence
     */
    @Query("SELECT s FROM StopSpacing s WHERE s.variantId = :variantId ORDER BY s.stopSequence ASC")
    fun findByVariantOrderBySequence(@Param("variantId") variantId: String): List<StopSpacing>

    /**
     * Find stop spacing for a specific consecutive stop pair on a variant.
     *
     * @param variantId The route variant ID
     * @param fromStopId The origin stop ID
     * @param toStopId The destination stop ID
     * @return StopSpacing record if found, null otherwise
     */
    @Query("SELECT s FROM StopSpacing s WHERE s.variantId = :variantId AND s.fromStopId = :fromStopId AND s.toStopId = :toStopId")
    fun findByVariantAndStopPair(
        @Param("variantId") variantId: String,
        @Param("fromStopId") fromStopId: String,
        @Param("toStopId") toStopId: String
    ): StopSpacing?

    /**
     * Delete all stop spacing records for a variant.
     *
     * Used when recalculating spacing for a variant.
     *
     * @param variantId The route variant ID
     */
    @Query("DELETE FROM StopSpacing s WHERE s.variantId = :variantId")
    fun deleteByVariant(@Param("variantId") variantId: String)

    /**
     * Check if stop spacing data exists for a variant.
     *
     * @param variantId The route variant ID to check
     * @return true if any records exist, false otherwise
     */
    @Query("SELECT CASE WHEN COUNT(s) > 0 THEN true ELSE false END FROM StopSpacing s WHERE s.variantId = :variantId")
    fun existsByVariant(@Param("variantId") variantId: String): Boolean

    /**
     * Count stop spacing records for a variant.
     *
     * @param variantId The route variant ID
     * @return Number of spacing records for the variant
     */
    @Query("SELECT COUNT(s) FROM StopSpacing s WHERE s.variantId = :variantId")
    fun countByVariant(@Param("variantId") variantId: String): Long
}
