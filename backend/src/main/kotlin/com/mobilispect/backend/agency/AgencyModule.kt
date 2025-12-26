package com.mobilispect.backend.agency

import org.springframework.modulith.ApplicationModule

/**
 * Agency Management Module.
 *
 * This module is responsible for:
 * - Managing transit agencies (operators) providing public transportation services
 * - Associating agencies with feeds
 * - Tracking agency metadata (name, website, contact information)
 *
 * Dependencies:
 * - feed: Uses FeedQueryApi to validate feed existence
 *
 * Public API:
 * - agency.api.AgencyQueryApi: Query interface for agency data
 * - agency.api.AgencyDTO: Data transfer object for cross-module communication
 *
 * Database Ownership:
 * - agencies table
 * - Foreign key reference to feeds.feed_onestop_id (no JPA navigation)
 *
 * Constitutional Requirement (Modular Monolith Ownership):
 * - No cross-module database access
 * - Communication via ports/events only
 * - Depends only on feed module (via API)
 */
@ApplicationModule(
    displayName = "Agency Management",
    allowedDependencies = ["feed"]
)
class AgencyModule
