package com.mobilispect.backend.route.data.mapper

import com.mobilispect.backend.route.RouteId
import com.mobilispect.backend.route.data.entity.RouteCommonSectionEntity
import com.mobilispect.backend.route.data.entity.RouteEntity
import com.mobilispect.backend.route.domain.model.RouteCommonSection
import org.springframework.stereotype.Component

/**
 * Mapper for bidirectional conversion between RouteCommonSection domain model
 * and RouteCommonSectionEntity data model.
 */
@Component
class RouteCommonSectionMapper {

  /** Converts data entity to domain model. Extracts ID from the route relationship. */
  fun toDomain(entity: RouteCommonSectionEntity): RouteCommonSection =
    RouteCommonSection(
      id = entity.id,
      routeId = RouteId(entity.route.id),
      directionId = entity.directionId,
      stopPattern = entity.stopPattern,
      stopNamePattern = entity.stopNamePattern,
      stopCount = entity.stopCount,
      firstStopId = entity.firstStopId,
      lastStopId = entity.lastStopId,
      variantCount = entity.variantCount,
      createdAt = entity.createdAt,
      updatedAt = entity.updatedAt,
    )

  /**
   * Converts domain model to data entity. Requires the route entity to be provided
   * by the caller for the relationship.
   *
   * @param domain The domain model to convert
   * @param routeEntity The route entity this common section belongs to
   */
  fun toEntity(domain: RouteCommonSection, routeEntity: RouteEntity): RouteCommonSectionEntity =
    RouteCommonSectionEntity(
      id = domain.id,
      route = routeEntity,
      directionId = domain.directionId,
      stopPattern = domain.stopPattern,
      stopNamePattern = domain.stopNamePattern,
      stopCount = domain.stopCount,
      firstStopId = domain.firstStopId,
      lastStopId = domain.lastStopId,
      variantCount = domain.variantCount,
      createdAt = domain.createdAt,
      updatedAt = domain.updatedAt,
    )
}
