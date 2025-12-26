package com.mobilispect.backend.agency.api

import com.mobilispect.backend.agency.domain.model.ids.AgencyId
import com.mobilispect.backend.feed.model.ids.FeedId

/**
 * Public API for querying agencies.
 *
 * This is the Agency module's API contract for cross-module communication.
 * Other modules should use this API instead of accessing repositories directly.
 *
 * Constitutional Requirement (Modular Monolith Ownership):
 * - No cross-module database access
 * - Communication via ports/events only
 */
interface AgencyQueryApi {
    /**
     * Find an agency by its onestop ID.
     *
     * @param agencyId The unique identifier for the agency
     * @return The agency DTO if found, null otherwise
     */
    fun findAgencyById(agencyId: AgencyId): AgencyDTO?

    /**
     * Find all agencies associated with a specific feed.
     *
     * @param feedId The feed identifier
     * @return List of agencies in the feed
     */
    fun findAgenciesByFeed(feedId: FeedId): List<AgencyDTO>
}
