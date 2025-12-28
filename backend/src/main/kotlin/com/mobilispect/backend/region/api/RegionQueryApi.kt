package com.mobilispect.backend.region.api

import com.mobilispect.backend.feed.model.ids.RegionId

/**
 * Public API for querying metropolitan regions.
 *
 * This is the Region module's API contract for cross-module communication. Other modules should use
 * this API instead of accessing repositories directly.
 *
 * Constitutional Requirement (Modular Monolith Ownership):
 * - No cross-module database access
 * - Communication via ports/events only
 */
interface RegionQueryApi {
  /**
   * Find a region by its onestop ID.
   *
   * @param regionId The unique identifier for the region
   * @return The region DTO if found, null otherwise
   */
  fun findRegionById(regionId: RegionId): RegionDTO?

  /**
   * Find all metropolitan regions.
   *
   * @return List of all regions
   */
  fun findAllRegions(): List<RegionDTO>
}
