package com.mobilispect.backend.agency.domain.repository

import com.mobilispect.backend.feed.model.FeedEntity
import com.mobilispect.backend.agency.domain.model.Agency
import com.mobilispect.backend.agency.domain.model.ids.AgencyId
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import java.time.Instant

/**
 * Repository for [Agency] entities in the transit analysis module.
 *
 * Provides data access methods for agencies, including queries by feed, GTFS ID,
 * active status, and import timestamps. All query methods support pagination
 * for efficient data retrieval in large datasets.
 *
 * Note: Agency IDs use Onestop ID format (o-geohash-name) and are stored as
 * value objects. Hibernate's findById may not properly convert these, so use
 * explicit @Query methods for ID-based lookups.
 */
@Repository
interface AgencyRepository : JpaRepository<Agency, AgencyId> {

    /**
     * Find all agencies for a specific feed.
     *
     * @param feed The feed entity to search within
     * @param pageable Pagination parameters
     * @return Page of agencies belonging to the specified feed
     */
    @Query("SELECT a FROM Agency a WHERE a.feed = :feed ORDER BY a.name ASC")
    fun findByFeed(
        @Param("feed") feed: FeedEntity,
        pageable: Pageable
    ): Page<Agency>

    /**
     * Find all active agencies for a specific feed.
     *
     * @param feed The feed entity to search within
     * @param pageable Pagination parameters
     * @return Page of active agencies belonging to the specified feed
     */
    @Query("SELECT a FROM Agency a WHERE a.feed = :feed AND a.active = true ORDER BY a.name ASC")
    fun findByFeedAndActive(
        @Param("feed") feed: FeedEntity,
        pageable: Pageable
    ): Page<Agency>

    /**
     * Find agency by feed and GTFS agency ID.
     *
     * GTFS agency ID is the identifier used in the GTFS feed's agency.txt file
     * and may differ from the Onestop ID.
     *
     * @param feed The feed entity to search within
     * @param gtfsAgencyId The GTFS agency ID from agency.txt
     * @return Agency if found, empty Optional otherwise
     */
    @Query("SELECT a FROM Agency a WHERE a.feed = :feed AND a.gtfsAgencyId = :gtfsAgencyId")
    fun findByFeedAndGtfsAgencyId(
        @Param("feed") feed: FeedEntity,
        @Param("gtfsAgencyId") gtfsAgencyId: String
    ): java.util.Optional<Agency>

    /**
     * Find all agencies with a specific active status.
     *
     * @param active Whether to search for active or inactive agencies
     * @param pageable Pagination parameters
     * @return Page of agencies with the specified active status
     */
    @Query("SELECT a FROM Agency a WHERE a.active = :active ORDER BY a.name ASC")
    fun findByActive(
        @Param("active") active: Boolean,
        pageable: Pageable
    ): Page<Agency>

    /**
     * Find agencies updated since a specific timestamp.
     *
     * Useful for incremental imports and change detection.
     *
     * @param since Instant timestamp to filter by
     * @param pageable Pagination parameters
     * @return Page of agencies updated since the specified time
     */
    @Query("SELECT a FROM Agency a WHERE a.updatedAt >= :since ORDER BY a.updatedAt DESC")
    fun findByUpdatedAtAfter(
        @Param("since") since: Instant,
        pageable: Pageable
    ): Page<Agency>

    /**
     * Find agencies with recent feed imports.
     *
     * @param after Instant timestamp for minimum feed import time
     * @param pageable Pagination parameters
     * @return Page of agencies with feed imports after the specified time
     */
    @Query("SELECT a FROM Agency a WHERE a.lastFeedImport >= :after ORDER BY a.lastFeedImport DESC")
    fun findByLastFeedImportAfter(
        @Param("after") after: Instant,
        pageable: Pageable
    ): Page<Agency>

    /**
     * Count agencies for a specific feed.
     *
     * @param feed The feed entity to count within
     * @return Number of agencies in the feed
     */
    @Query("SELECT COUNT(a) FROM Agency a WHERE a.feed = :feed")
    fun countByFeed(@Param("feed") feed: FeedEntity): Long

    /**
     * Count active agencies for a specific feed.
     *
     * @param feed The feed entity to count within
     * @return Number of active agencies in the feed
     */
    @Query("SELECT COUNT(a) FROM Agency a WHERE a.feed = :feed AND a.active = true")
    fun countActiveByFeed(@Param("feed") feed: FeedEntity): Long

    /**
     * Check if an agency exists for a specific feed and GTFS ID.
     *
     * @param feed The feed entity to search within
     * @param gtfsAgencyId The GTFS agency ID
     * @return true if the agency exists, false otherwise
     */
    @Query("SELECT CASE WHEN COUNT(a) > 0 THEN true ELSE false END FROM Agency a WHERE a.feed = :feed AND a.gtfsAgencyId = :gtfsAgencyId")
    fun existsByFeedAndGtfsAgencyId(
        @Param("feed") feed: FeedEntity,
        @Param("gtfsAgencyId") gtfsAgencyId: String
    ): Boolean
}
