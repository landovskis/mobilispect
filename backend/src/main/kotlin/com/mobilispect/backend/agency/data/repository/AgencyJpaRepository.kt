package com.mobilispect.backend.agency.data.repository

import com.mobilispect.backend.agency.data.entity.AgencyEntity
import java.time.Instant
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

/**
 * JPA repository for [AgencyEntity] data layer.
 *
 * Provides data access for agency entities using plain String IDs. Used by AgencyRepository
 * implementation to persist and retrieve domain models.
 */
interface AgencyJpaRepository : JpaRepository<AgencyEntity, String> {

  /** Find all agencies for a specific feed. */
  @Query("SELECT a FROM AgencyEntity a WHERE a.feedId = :feedId ORDER BY a.name ASC")
  fun findByFeedId(@Param("feedId") feedId: String, pageable: Pageable): Page<AgencyEntity>

  /** Find all active agencies for a specific feed. */
  @Query(
    "SELECT a FROM AgencyEntity a WHERE a.feedId = :feedId AND a.active = true ORDER BY a.name ASC"
  )
  fun findByFeedIdAndActive(@Param("feedId") feedId: String, pageable: Pageable): Page<AgencyEntity>

  /** Find agency by feed ID and GTFS agency ID. */
  @Query("SELECT a FROM AgencyEntity a WHERE a.feedId = :feedId AND a.gtfsId = :gtfsAgencyId")
  fun findByFeedIdAndGtfsAgencyId(
    @Param("feedId") feedId: String,
    @Param("gtfsAgencyId") gtfsAgencyId: String,
  ): AgencyEntity?

  /** Find all agencies with a specific active status. */
  @Query("SELECT a FROM AgencyEntity a WHERE a.active = :active ORDER BY a.name ASC")
  fun findByActive(@Param("active") active: Boolean, pageable: Pageable): Page<AgencyEntity>

  /** Find agencies updated since a specific timestamp. */
  @Query("SELECT a FROM AgencyEntity a WHERE a.updatedAt >= :since ORDER BY a.updatedAt DESC")
  fun findByUpdatedAtAfter(@Param("since") since: Instant, pageable: Pageable): Page<AgencyEntity>

  /** Find agencies with recent feed imports. */
  @Query(
    "SELECT a FROM AgencyEntity a WHERE a.lastFeedImport >= :after ORDER BY a.lastFeedImport DESC"
  )
  fun findByLastFeedImportAfter(
    @Param("after") after: Instant,
    pageable: Pageable,
  ): Page<AgencyEntity>

  /** Count agencies for a specific feed. */
  @Query("SELECT COUNT(a) FROM AgencyEntity a WHERE a.feedId = :feedId")
  fun countByFeedId(@Param("feedId") feedId: String): Long

  /** Count active agencies for a specific feed. */
  @Query("SELECT COUNT(a) FROM AgencyEntity a WHERE a.feedId = :feedId AND a.active = true")
  fun countActiveByFeedId(@Param("feedId") feedId: String): Long

  /** Check if an agency exists for a specific feed and GTFS ID. */
  @Query(
    "SELECT CASE WHEN COUNT(a) > 0 THEN true ELSE false END FROM AgencyEntity a WHERE a.feedId = :feedId AND a.gtfsId = :gtfsAgencyId"
  )
  fun existsByFeedIdAndGtfsAgencyId(
    @Param("feedId") feedId: String,
    @Param("gtfsAgencyId") gtfsAgencyId: String,
  ): Boolean
}
