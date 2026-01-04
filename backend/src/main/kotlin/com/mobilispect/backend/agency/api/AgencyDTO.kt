package com.mobilispect.backend.agency.api

import com.mobilispect.backend.agency.AgencyId
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
    val name: String,
    val active: Boolean,
    val createdAt: Instant,
    val updatedAt: Instant,
)
