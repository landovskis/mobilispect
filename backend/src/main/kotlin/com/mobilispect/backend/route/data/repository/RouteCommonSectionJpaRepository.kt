package com.mobilispect.backend.route.data.repository

import com.mobilispect.backend.route.data.entity.RouteCommonSectionEntity
import com.mobilispect.backend.route.data.entity.RouteEntity
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

/**
 * JPA repository for [RouteCommonSectionEntity] data layer.
 *
 * Provides data access for route common section entities using plain String IDs.
 */
interface RouteCommonSectionJpaRepository : JpaRepository<RouteCommonSectionEntity, String> {

  /** Find common section for a specific route and direction. */
  @Query(
    "SELECT rcs FROM RouteCommonSectionEntity rcs WHERE rcs.route = :route AND " +
      "(:directionId IS NULL AND rcs.directionId IS NULL OR rcs.directionId = :directionId)"
  )
  fun findByRouteAndDirectionId(
    @Param("route") route: RouteEntity,
    @Param("directionId") directionId: Int?,
  ): RouteCommonSectionEntity?

  /** Find all common sections for a specific route. */
  @Query(
    "SELECT rcs FROM RouteCommonSectionEntity rcs WHERE rcs.route = :route " +
      "ORDER BY rcs.directionId ASC NULLS LAST"
  )
  fun findByRoute(@Param("route") route: RouteEntity): List<RouteCommonSectionEntity>

  /** Delete all common sections for a specific route. */
  @Modifying
  @Query("DELETE FROM RouteCommonSectionEntity rcs WHERE rcs.route = :route")
  fun deleteByRoute(@Param("route") route: RouteEntity)
}
