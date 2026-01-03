package com.mobilispect.backend.stop.data.repository

import com.mobilispect.backend.stop.data.entity.StopEntity
import java.time.Instant
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

/**
 * JPA repository for [StopEntity] data layer.
 *
 * Provides data access for stop entities using plain String IDs. Used by StopRepository
 * implementation to persist and retrieve domain models.
 */
interface StopJpaRepository : JpaRepository<StopEntity, String> {

  /** Find all stops for a specific feed. */
  @Query("SELECT s FROM StopEntity s WHERE s.feedOnestopId = :feedId ORDER BY s.name ASC")
  fun findByFeedId(@Param("feedId") feedId: String, pageable: Pageable): Page<StopEntity>

  /** Find all active stops for a specific feed. */
  @Query(
    "SELECT s FROM StopEntity s WHERE s.feedOnestopId = :feedId AND s.active = true ORDER BY s.name ASC"
  )
  fun findByFeedIdAndActive(@Param("feedId") feedId: String, pageable: Pageable): Page<StopEntity>

  /** Find stop by feed ID and GTFS stop ID. */
  @Query(
    "SELECT s FROM StopEntity s WHERE s.feedOnestopId = :feedId AND s.gtfsStopId = :gtfsStopId"
  )
  fun findByFeedIdAndGtfsStopId(
    @Param("feedId") feedId: String,
    @Param("gtfsStopId") gtfsStopId: String,
  ): StopEntity?

  /** Find all stops with a specific active status. */
  @Query("SELECT s FROM StopEntity s WHERE s.active = :active ORDER BY s.name ASC")
  fun findByActive(@Param("active") active: Boolean, pageable: Pageable): Page<StopEntity>

  /** Find stops within a bounding box (for geospatial queries). */
  @Query(
    """
        SELECT s FROM StopEntity s
        WHERE s.latitude BETWEEN :minLat AND :maxLat
        AND s.longitude BETWEEN :minLon AND :maxLon
        ORDER BY s.name ASC
    """
  )
  fun findByBoundingBox(
    @Param("minLat") minLat: Double,
    @Param("minLon") minLon: Double,
    @Param("maxLat") maxLat: Double,
    @Param("maxLon") maxLon: Double,
  ): List<StopEntity>

  /** Find stops updated since a specific timestamp. */
  @Query("SELECT s FROM StopEntity s WHERE s.updatedAt >= :since ORDER BY s.updatedAt DESC")
  fun findByUpdatedAtAfter(@Param("since") since: Instant, pageable: Pageable): Page<StopEntity>

  /** Find stops last seen after a specific timestamp (active in recent imports). */
  @Query("SELECT s FROM StopEntity s WHERE s.lastSeen >= :after ORDER BY s.lastSeen DESC")
  fun findByLastSeenAfter(@Param("after") after: Instant, pageable: Pageable): Page<StopEntity>

  /** Find stops by location type (0=stop, 1=station, 2=entrance, 3=node, 4=boarding area). */
  @Query(
    "SELECT s FROM StopEntity s WHERE s.feedOnestopId = :feedId AND s.locationType = :locationType ORDER BY s.name ASC"
  )
  fun findByFeedIdAndLocationType(
    @Param("feedId") feedId: String,
    @Param("locationType") locationType: Int,
    pageable: Pageable,
  ): Page<StopEntity>

  /** Find all stops that are stations (location_type = 1). */
  @Query(
    "SELECT s FROM StopEntity s WHERE s.feedOnestopId = :feedId AND s.locationType = 1 ORDER BY s.name ASC"
  )
  fun findStationsByFeedId(@Param("feedId") feedId: String, pageable: Pageable): Page<StopEntity>

  /** Count stops for a specific feed. */
  @Query("SELECT COUNT(s) FROM StopEntity s WHERE s.feedOnestopId = :feedId")
  fun countByFeedId(@Param("feedId") feedId: String): Long

  /** Count active stops for a specific feed. */
  @Query("SELECT COUNT(s) FROM StopEntity s WHERE s.feedOnestopId = :feedId AND s.active = true")
  fun countActiveByFeedId(@Param("feedId") feedId: String): Long

  /** Check if a stop exists for a specific feed and GTFS ID. */
  @Query(
    "SELECT CASE WHEN COUNT(s) > 0 THEN true ELSE false END FROM StopEntity s WHERE s.feedOnestopId = :feedId AND s.gtfsStopId = :gtfsStopId"
  )
  fun existsByFeedIdAndGtfsStopId(
    @Param("feedId") feedId: String,
    @Param("gtfsStopId") gtfsStopId: String,
  ): Boolean
}
