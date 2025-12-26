package com.mobilispect.backend.feed

import org.springframework.modulith.ApplicationModule

/**
 * Feed Management Module.
 *
 * This module is responsible for:
 * - Discovering feeds from Transit.land API
 * - Managing feed lifecycle (import, version tracking, authentication)
 * - Storing feed metadata and download information
 *
 * Dependencies:
 * - region: Uses RegionQueryApi for region lookups; publishes RegionDiscoveredEvent
 *
 * Public API:
 * - feed.api.FeedQueryApi: Query interface for feed data
 * - feed.api.FeedDTO: Data transfer object for cross-module communication
 *
 * Events Published:
 * - RegionDiscoveredEvent: When new regions are discovered during feed discovery
 *
 * Database Ownership:
 * - feeds table
 * - feed_regions junction table (many-to-many with regions)
 * - feed_authentications table
 * - feed_imports table
 *
 * Constitutional Requirement (Modular Monolith Ownership):
 * - No cross-module database access (except via events)
 * - Communication via ports/events only
 * - Depends only on region module
 */
@ApplicationModule(
    displayName = "Feed Management",
    allowedDependencies = ["region"]
)
class FeedModule
