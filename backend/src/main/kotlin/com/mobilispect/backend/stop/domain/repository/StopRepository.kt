package com.mobilispect.backend.stop.domain.repository

import com.mobilispect.backend.feed.domain.model.ids.FeedId
import com.mobilispect.backend.stop.domain.model.Stop
import com.mobilispect.backend.stop.domain.model.ids.StopId
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import java.time.Instant

/**
 * Domain repository for [Stop] entities.
 *
 * Provides data access methods for transit stops using domain models and type-safe value class IDs.
 * Implementation delegates to JPA repositories and uses mappers for domain/data conversion.
 */
interface StopRepository {

    /**
     * Find stop by its Onestop ID.
     */
    fun findById(stopId: StopId): Stop?

    /**
     * Find all stops for a specific feed.
     */
    fun findByFeedId(feedId: FeedId, pageable: Pageable): Page<Stop>

    /**
     * Find all active stops for a specific feed.
     */
    fun findByFeedIdAndActive(feedId: FeedId, pageable: Pageable): Page<Stop>

    /**
     * Find stop by feed and GTFS stop ID.
     */
    fun findByFeedIdAndGtfsStopId(feedId: FeedId, gtfsStopId: String): Stop?

    /**
     * Find all stops with a specific active status.
     */
    fun findByActive(active: Boolean, pageable: Pageable): Page<Stop>

    /**
     * Find stops within a bounding box (for geospatial queries).
     *
     * @param minLat Minimum latitude (southwest corner)
     * @param minLon Minimum longitude (southwest corner)
     * @param maxLat Maximum latitude (northeast corner)
     * @param maxLon Maximum longitude (northeast corner)
     * @return List of stops within the bounding box
     */
    fun findByBoundingBox(minLat: Double, minLon: Double, maxLat: Double, maxLon: Double): List<Stop>

    /**
     * Find stops updated since a specific timestamp.
     */
    fun findByUpdatedAtAfter(since: Instant, pageable: Pageable): Page<Stop>

    /**
     * Find stops last seen after a specific timestamp (active in recent imports).
     */
    fun findByLastSeenAfter(after: Instant, pageable: Pageable): Page<Stop>

    /**
     * Find stops by location type (0=stop, 1=station, 2=entrance, 3=node, 4=boarding area).
     */
    fun findByFeedIdAndLocationType(feedId: FeedId, locationType: Int, pageable: Pageable): Page<Stop>

    /**
     * Find all stops that are stations (location_type = 1).
     */
    fun findStationsByFeedId(feedId: FeedId, pageable: Pageable): Page<Stop>

    /**
     * Count stops for a specific feed.
     */
    fun countByFeedId(feedId: FeedId): Long

    /**
     * Count active stops for a specific feed.
     */
    fun countActiveByFeedId(feedId: FeedId): Long

    /**
     * Check if a stop exists for a specific feed and GTFS ID.
     */
    fun existsByFeedIdAndGtfsStopId(feedId: FeedId, gtfsStopId: String): Boolean

    /**
     * Save a stop.
     */
    fun save(stop: Stop): Stop

    /**
     * Delete a stop by ID.
     */
    fun deleteById(stopId: StopId)

    /**
     * Find all stops.
     */
    fun findAll(): List<Stop>
}
