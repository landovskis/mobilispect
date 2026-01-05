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
      agencyId = AgencyId(entity.id),
      feedId = FeedId(entity.feedId),
      name = entity.name,
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
      id = domain.agencyId.toString(),
      feedId = domain.feedId.value,
      name = domain.name,
      active = domain.active,
      createdAt = domain.createdAt,
      updatedAt = domain.updatedAt,
    )
}
