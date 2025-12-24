package com.mobilispect.backend.agency.data.mapper

import com.mobilispect.backend.agency.data.entity.AgencyEntity
import com.mobilispect.backend.agency.domain.model.Agency
import com.mobilispect.backend.agency.domain.model.ids.AgencyId
import com.mobilispect.backend.feed.data.entity.FeedEntity
import com.mobilispect.backend.feed.model.ids.FeedId
import org.springframework.stereotype.Component

/**
 * Mapper for bidirectional conversion between Agency domain model and AgencyEntity data model.
 *
 * Domain models use @JvmInline value class IDs for type safety.
 * Data entities use plain String IDs for Hibernate 7 compatibility.
 */
@Component
class AgencyMapper {

    /**
     * Converts data entity to domain model.
     * Extracts ID from the feed relationship.
     */
    fun toDomain(entity: AgencyEntity): Agency =
        Agency(
            agencyOnestopId = AgencyId(entity.agencyOnestopId),
            feedId = FeedId(entity.feed.feedOnestopId),
            gtfsAgencyId = entity.gtfsAgencyId,
            name = entity.name,
            website = entity.website,
            phone = entity.phone,
            lastFeedImport = entity.lastFeedImport,
            active = entity.active,
            createdAt = entity.createdAt,
            updatedAt = entity.updatedAt
        )

    /**
     * Converts domain model to data entity.
     * Requires the feed entity to be provided by the caller for the relationship.
     *
     * @param domain The domain model to convert
     * @param feedEntity The feed entity this agency belongs to
     */
    fun toEntity(domain: Agency, feedEntity: FeedEntity): AgencyEntity =
        AgencyEntity(
            agencyOnestopId = domain.agencyOnestopId.value,
            feed = feedEntity,
            gtfsAgencyId = domain.gtfsAgencyId,
            name = domain.name,
            website = domain.website,
            phone = domain.phone,
            lastFeedImport = domain.lastFeedImport,
            active = domain.active,
            createdAt = domain.createdAt,
            updatedAt = domain.updatedAt
        )
}
