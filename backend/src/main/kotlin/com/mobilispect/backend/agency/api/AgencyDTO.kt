package com.mobilispect.backend.agency.api

import com.mobilispect.backend.agency.domain.model.ids.AgencyId
import com.mobilispect.backend.feed.api.ids.GTFSAgencyId
import com.mobilispect.backend.feed.domain.model.ids.FeedId
import java.time.Instant

/**
 * Data Transfer Object for Agency.
 *
 * Exposes agency data across module boundaries without exposing internal entities. Part of the
 * Agency module's public API.
 */
data class AgencyDTO(
  val agencyId: AgencyId,
  val feedId: FeedId,
  val gtfsAgencyId: GTFSAgencyId,
  val name: String,
  val website: String?,
  val phone: String?,
  val lastFeedImport: Instant?,
  val active: Boolean,
  val createdAt: Instant,
  val updatedAt: Instant,
)
