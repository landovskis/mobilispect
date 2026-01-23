package com.mobilispect.backend.agency.data.mapper

import com.mobilispect.backend.agency.AgencyId
import com.mobilispect.backend.agency.data.entity.AgencyEntity
import com.mobilispect.backend.agency.domain.model.Agency
import com.mobilispect.backend.feed.domain.model.ids.FeedId
import org.springframework.stereotype.Component

/**
 * Mapper for bidirectional conversion between Agency domain model and AgencyEntity data model.
 *
 * Domain models use @JvmInline value class IDs for type safety. Data entities use plain String IDs
 * for Hibernate 7 compatibility.
 */
@Component
class AgencyMapper {

  /** Converts data entity to the domain model. */
  fun toDomain(entity: AgencyEntity): Agency =
    Agency(
      agencyId = AgencyId(entity.agencyOnestopId),
      feedId = FeedId(entity.feedId),
      gtfsAgencyId = entity.gtfsId,
      name = entity.name,
      website = entity.website,
      phone = entity.phone,
      lastFeedImport = entity.lastFeedImport,
      active = entity.active,
      createdAt = entity.createdAt,
      updatedAt = entity.updatedAt,
    )

  /**
   * Converts the domain model to the data entity.
   *
   * @param domain The domain model to convert
   */
  fun toEntity(domain: Agency): AgencyEntity =
    AgencyEntity(
      agencyOnestopId = domain.agencyId.value,
      feedId = domain.feedId.value,
      gtfsId = domain.gtfsAgencyId,
      name = domain.name,
      website = domain.website,
      phone = domain.phone,
      lastFeedImport = domain.lastFeedImport,
      active = domain.active,
      createdAt = domain.createdAt,
      updatedAt = domain.updatedAt,
    )
}
