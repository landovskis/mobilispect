package com.mobilispect.backend.route.domain.repository

import com.mobilispect.backend.route.domain.model.VariantDeparture
import java.util.UUID
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository

/**
 * Repository for [VariantDeparture] entities.
 *
 * Provides data access methods for individual variant departure times.
 */
@Repository
interface VariantDepartureRepository : JpaRepository<VariantDeparture, UUID> {

  /**
   * Find all departure times for a specific route variant, ordered by time.
   *
   * @param variantId The route variant ID to search for
   * @return List of departures sorted by departure time
   */
  @Query(
    "SELECT d FROM VariantDeparture d WHERE d.variantId = :variantId ORDER BY d.departureTime ASC"
  )
  fun findByVariantIdOrderByDepartureTime(
    @Param("variantId") variantId: String
  ): List<VariantDeparture>

  /**
   * Delete all departure times for a variant.
   *
   * Used when recalculating schedule for a variant.
   *
   * @param variantId The route variant ID
   */
  @Query("DELETE FROM VariantDeparture d WHERE d.variantId = :variantId")
  @Modifying
  fun deleteByVariantId(@Param("variantId") variantId: String)

  /**
   * Check if departure times exist for a variant.
   *
   * @param variantId The route variant ID to check
   * @return true if records exist, false otherwise
   */
  @Query(
    "SELECT CASE WHEN COUNT(d) > 0 THEN true ELSE false END FROM VariantDeparture d WHERE d.variantId = :variantId"
  )
  fun existsByVariantId(@Param("variantId") variantId: String): Boolean

  /**
   * Count departure times for a variant.
   *
   * @param variantId The route variant ID
   * @return Number of departures
   */
  @Query("SELECT COUNT(d) FROM VariantDeparture d WHERE d.variantId = :variantId")
  fun countByVariantId(@Param("variantId") variantId: String): Long
}
