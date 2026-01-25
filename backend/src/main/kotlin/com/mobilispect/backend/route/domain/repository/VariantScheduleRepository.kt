package com.mobilispect.backend.route.domain.repository

import com.mobilispect.backend.route.domain.model.VariantSchedule
import java.util.UUID
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository

/**
 * Repository for [VariantSchedule] entities.
 *
 * Provides data access methods for variant schedule summaries.
 */
@Repository
interface VariantScheduleRepository : JpaRepository<VariantSchedule, UUID> {

  /**
   * Find schedule summary for a specific route variant.
   *
   * @param variantId The route variant ID to search for
   * @return VariantSchedule if found, null otherwise
   */
  @Query("SELECT s FROM VariantSchedule s WHERE s.variantId = :variantId")
  fun findByVariantId(@Param("variantId") variantId: String): VariantSchedule?

  /**
   * Delete schedule summary for a variant.
   *
   * Used when recalculating schedule for a variant.
   *
   * @param variantId The route variant ID
   */
  @Query("DELETE FROM VariantSchedule s WHERE s.variantId = :variantId")
  @Modifying
  fun deleteByVariantId(@Param("variantId") variantId: String)

  /**
   * Check if schedule summary exists for a variant.
   *
   * @param variantId The route variant ID to check
   * @return true if a record exists, false otherwise
   */
  @Query(
    "SELECT CASE WHEN COUNT(s) > 0 THEN true ELSE false END FROM VariantSchedule s WHERE s.variantId = :variantId"
  )
  fun existsByVariantId(@Param("variantId") variantId: String): Boolean
}
