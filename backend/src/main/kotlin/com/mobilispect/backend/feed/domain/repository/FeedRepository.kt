package com.mobilispect.backend.feed.domain.repository

import com.mobilispect.backend.feed.domain.model.Feed
import com.mobilispect.backend.feed.domain.model.ids.FeedId
import com.mobilispect.backend.feed.model.FeedSpecType
import com.mobilispect.backend.feed.model.FeedStatus
import com.mobilispect.backend.feed.model.ids.RegionId

/**
 * Domain repository for [Feed] entities.
 *
 * Provides data access methods for feeds using domain models and type-safe value class IDs.
 * Implementation delegates to JPA repositories and uses mappers for domain/data conversion.
 *
 * Per ADR 0009, this repository works with domain models that use FK-only pattern for regions.
 */
interface FeedRepository {

    /**
     * Find feed by its Onestop ID.
     *
     * @param feedId The feed identifier
     * @return The feed domain model, or null if not found
     */
    fun findById(feedId: FeedId): Feed?

    /**
     * Find all feeds for a specific region.
     *
     * @param regionId The region identifier
     * @return List of feeds associated with the region
     */
    fun findByRegionId(regionId: RegionId): List<Feed>

    /**
     * Find all feeds by region and status.
     *
     * @param regionId The region identifier
     * @param statuses Collection of feed statuses to filter by
     * @return List of feeds matching the criteria
     */
    fun findByRegionIdAndStatusIn(regionId: RegionId, statuses: Collection<FeedStatus>): List<Feed>

    /**
     * Find all feeds by region and spec type.
     *
     * @param regionId The region identifier
     * @param specTypes Collection of feed specification types to filter by
     * @return List of feeds matching the criteria
     */
    fun findByRegionIdAndSpecTypeIn(regionId: RegionId, specTypes: Collection<FeedSpecType>): List<Feed>

    /**
     * Count feeds for a specific region.
     *
     * @param regionId The region identifier
     * @return Number of feeds associated with the region
     */
    fun countByRegionId(regionId: RegionId): Long

    /**
     * Save a feed.
     *
     * For new feeds, creates a new record. For existing feeds, updates the record.
     * Manages the many-to-many relationship with regions through the junction table.
     *
     * @param feed The feed domain model to save
     * @return The saved feed domain model
     */
    fun save(feed: Feed): Feed

    /**
     * Delete a feed by ID.
     *
     * @param feedId The feed identifier
     */
    fun deleteById(feedId: FeedId)

    /**
     * Find all feeds.
     *
     * @return List of all feeds
     */
    fun findAll(): List<Feed>
}
