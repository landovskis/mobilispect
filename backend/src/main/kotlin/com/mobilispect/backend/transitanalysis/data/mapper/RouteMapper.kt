package com.mobilispect.backend.transitanalysis.data.mapper

import com.mobilispect.backend.agency.data.entity.AgencyEntity
import com.mobilispect.backend.agency.domain.model.ids.AgencyId
import com.mobilispect.backend.transitanalysis.data.entity.RouteEntity
import com.mobilispect.backend.transitanalysis.domain.model.Route
import com.mobilispect.backend.transitanalysis.domain.model.RouteType
import com.mobilispect.backend.transitanalysis.domain.model.ids.RouteId
import org.springframework.stereotype.Component

/**
 * Mapper for bidirectional conversion between Route domain model and RouteEntity data model.
 *
 * Domain models use @JvmInline value class IDs for type safety.
 * Data entities use plain String IDs for Hibernate 7 compatibility.
 */
@Component
class RouteMapper {

    /**
     * Converts data entity to domain model.
     * Extracts ID from the agency relationship and converts RouteType from String.
     */
    fun toDomain(entity: RouteEntity): Route =
        Route(
            id = RouteId(entity.id),
            agencyId = AgencyId(entity.agency.agencyOnestopId),
            gtfsRouteId = entity.gtfsRouteId,
            shortName = entity.shortName,
            longName = entity.longName,
            routeType = RouteType.fromValue(entity.routeType),
            color = entity.color,
            textColor = entity.textColor,
            active = entity.active,
            createdAt = entity.createdAt,
            updatedAt = entity.updatedAt
        )

    /**
     * Converts domain model to data entity.
     * Requires the agency entity to be provided by the caller for the relationship.
     *
     * @param domain The domain model to convert
     * @param agencyEntity The agency entity this route belongs to
     */
    fun toEntity(domain: Route, agencyEntity: AgencyEntity): RouteEntity =
        RouteEntity(
            id = domain.id.value,
            agency = agencyEntity,
            gtfsRouteId = domain.gtfsRouteId,
            shortName = domain.shortName,
            longName = domain.longName,
            routeType = domain.routeType.value,
            color = domain.color,
            textColor = domain.textColor,
            active = domain.active,
            createdAt = domain.createdAt,
            updatedAt = domain.updatedAt
        )
}
