package com.mobilispect.backend.route.data.mapper

import com.mobilispect.backend.agency.AgencyId
import com.mobilispect.backend.route.RouteId
import com.mobilispect.backend.route.data.entity.RouteEntity
import com.mobilispect.backend.route.domain.model.Route
import com.mobilispect.backend.route.domain.model.RouteType
import org.springframework.stereotype.Component

/**
 * Mapper for bidirectional conversion between Route domain model and RouteEntity data model.
 *
 * Domain models use @JvmInline value class IDs for type safety. Data entities use plain String IDs
 * for Hibernate 7 compatibility.
 */
@Component
class RouteMapper {

  /** Converts data entity to domain model. */
  fun toDomain(entity: RouteEntity): Route =
    Route(
      id = RouteId(entity.id),
      agencyId = AgencyId(entity.agencyId),
      gtfsRouteId = RouteId(entity.id).feedLocalId().value,
      shortName = entity.shortName,
      longName = entity.longName,
      routeType = RouteType.fromValue(entity.routeType),
      color = entity.color,
      textColor = entity.textColor,
      active = entity.active,
      createdAt = entity.createdAt,
      updatedAt = entity.updatedAt,
    )

  /**
   * Converts domain model to data entity. Maps agency ID directly to agencyOnestopId column without
   * entity navigation.
   *
   * @param domain The domain model to convert
   */
  fun toEntity(domain: Route): RouteEntity =
    RouteEntity(
      id = domain.id.value,
      agencyId = domain.agencyId.value,
      shortName = domain.shortName,
      longName = domain.longName,
      routeType = domain.routeType.value,
      color = domain.color,
      textColor = domain.textColor,
      active = domain.active,
      createdAt = domain.createdAt,
      updatedAt = domain.updatedAt,
    )
}
