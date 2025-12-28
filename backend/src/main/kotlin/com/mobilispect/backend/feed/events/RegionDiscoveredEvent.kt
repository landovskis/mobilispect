package com.mobilispect.backend.feed.events

import com.mobilispect.backend.feed.model.ids.RegionId

/**
 * Domain event published when a new region is discovered during feed discovery.
 *
 * This event allows the Region module to handle region creation internally, maintaining proper
 * module boundaries. The Feed module does not directly access region repositories.
 *
 * Constitutional Requirement (Modular Monolith Ownership):
 * - No cross-module database access
 * - Communication via ports/events only
 */
data class RegionDiscoveredEvent(
  val regionId: RegionId,
  val name: String,
  val adm0Name: String?,
  val adm1Name: String?,
  val operatorName: String? = null,
)
