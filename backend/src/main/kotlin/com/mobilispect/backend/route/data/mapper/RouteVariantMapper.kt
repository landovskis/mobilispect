package com.mobilispect.backend.route.data.mapper

import com.mobilispect.backend.route.data.entity.RouteEntity
import com.mobilispect.backend.route.data.entity.RouteVariantEntity
import com.mobilispect.backend.route.domain.model.RouteVariant
import com.mobilispect.backend.route.domain.model.ids.RouteId
import com.mobilispect.backend.route.domain.model.ids.VariantHash
import org.springframework.stereotype.Component

/**
 * Mapper for bidirectional conversion between RouteVariant domain model and RouteVariantEntity data model.
 *
 * Domain models use @JvmInline value class IDs for type safety.
 * Data entities use plain String IDs for Hibernate 7 compatibility.
 */
@Component
class RouteVariantMapper {

    /**
     * Converts data entity to domain model.
     * Extracts ID from the route relationship.
     */
    fun toDomain(entity: RouteVariantEntity): RouteVariant =
        RouteVariant(
            id = VariantHash(entity.id),
            routeId = RouteId(entity.route.id),
            directionId = entity.directionId,
            headsign = entity.headsign,
            stopPattern = entity.stopPattern,
            stopNamePattern = entity.stopNamePattern,
            stopCount = entity.stopCount,
            firstStopId = entity.firstStopId,
            lastStopId = entity.lastStopId,
            averageStopSpacingKm = entity.averageStopSpacingKm,
            active = entity.active,
            firstSeen = entity.firstSeen,
            lastSeen = entity.lastSeen,
            createdAt = entity.createdAt,
            updatedAt = entity.updatedAt
        )

    /**
     * Converts domain model to data entity.
     * Requires the route entity to be provided by the caller for the relationship.
     *
     * @param domain The domain model to convert
     * @param routeEntity The route entity this variant belongs to
     */
    fun toEntity(domain: RouteVariant, routeEntity: RouteEntity): RouteVariantEntity =
        RouteVariantEntity(
            id = domain.id.value,
            route = routeEntity,
            directionId = domain.directionId,
            headsign = domain.headsign,
            stopPattern = domain.stopPattern,
            stopNamePattern = domain.stopNamePattern,
            stopCount = domain.stopCount,
            firstStopId = domain.firstStopId,
            lastStopId = domain.lastStopId,
            averageStopSpacingKm = domain.averageStopSpacingKm,
            active = domain.active,
            firstSeen = domain.firstSeen,
            lastSeen = domain.lastSeen,
            createdAt = domain.createdAt,
            updatedAt = domain.updatedAt
        )
}
