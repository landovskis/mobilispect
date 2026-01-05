package com.mobilispect.backend.agency.internal

import com.mobilispect.backend.agency.AgencyId
import com.mobilispect.backend.agency.api.AgencyDTO
import com.mobilispect.backend.agency.api.AgencyQueryApi
import com.mobilispect.backend.agency.domain.model.Agency
import com.mobilispect.backend.agency.domain.repository.AgencyRepository
import com.mobilispect.backend.feed.domain.model.ids.FeedId
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Service

/**
 * Implementation of the Agency module's public query API.
 *
 * This service provides cross-module access to agency data while maintaining proper module
 * boundaries. It converts internal domain models to public DTOs.
 */
@Service
internal class AgencyQueryApiImpl(private val agencyRepository: AgencyRepository) : AgencyQueryApi {

  override fun findAgencyById(agencyId: AgencyId): AgencyDTO? {
    return agencyRepository.findById(agencyId)?.toDTO()
  }

  override fun findAgenciesByFeed(feedId: FeedId): List<AgencyDTO> {
    return agencyRepository.findByFeedId(feedId, Pageable.unpaged()).content.map { it.toDTO() }
  }

  /** Converts internal domain model to public DTO. */
  private fun Agency.toDTO(): AgencyDTO {
    return AgencyDTO(
      agencyId = this.agencyOnestopId,
      feedId = this.feedId,
      gtfsAgencyId = this.gtfsAgencyId,
      name = this.name,
      website = this.website,
      phone = this.phone,
      lastFeedImport = this.lastFeedImport,
      active = this.active,
      createdAt = this.createdAt,
      updatedAt = this.updatedAt,
    )
  }
}
