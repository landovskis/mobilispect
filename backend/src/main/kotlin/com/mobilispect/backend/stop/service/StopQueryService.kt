package com.mobilispect.backend.stop.service

import com.mobilispect.backend.config.RedisConfiguration
import com.mobilispect.backend.feed.domain.model.ids.FeedId
import com.mobilispect.backend.stop.api.StopDTO
import com.mobilispect.backend.stop.api.StopSummaryDTO
import com.mobilispect.backend.stop.domain.model.ids.StopId
import com.mobilispect.backend.stop.domain.repository.StopRepository
import org.springframework.cache.annotation.Cacheable
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Service

/**
 * Query service for stop-related operations with Redis caching.
 *
 * All query methods are cached with 1-hour TTL to improve performance.
 * Cache is invalidated when feed imports complete.
 */
@Service
class StopQueryService(
    private val stopRepository: StopRepository
) {

    /**
     * Get a stop by its Onestop ID.
     */
    @Cacheable(value = [RedisConfiguration.Companion.FREQUENCY_CACHE], key = "'stop_' + #stopId")
    fun getStop(stopId: StopId): StopDTO? =
        stopRepository.findById(stopId)?.let { StopDTO.fromDomain(it) }

    /**
     * Get all stops for a specific feed.
     */
    @Cacheable(value = [RedisConfiguration.Companion.FREQUENCY_CACHE], key = "'feed_stops_' + #feedId + '_' + #pageable.pageNumber")
    fun getStopsByFeed(feedId: FeedId, pageable: Pageable): Page<StopSummaryDTO> =
        stopRepository.findByFeedId(feedId, pageable)
            .map { StopSummaryDTO.fromDomain(it) }

    /**
     * Get all active stops for a specific feed.
     */
    @Cacheable(value = [RedisConfiguration.Companion.FREQUENCY_CACHE], key = "'feed_active_stops_' + #feedId + '_' + #pageable.pageNumber")
    fun getActiveStopsByFeed(feedId: FeedId, pageable: Pageable): Page<StopSummaryDTO> =
        stopRepository.findByFeedIdAndActive(feedId, pageable)
            .map { StopSummaryDTO.fromDomain(it) }

    /**
     * Get stops within a bounding box (for map views).
     *
     * @param minLat Southwest corner latitude
     * @param minLon Southwest corner longitude
     * @param maxLat Northeast corner latitude
     * @param maxLon Northeast corner longitude
     * @return List of stops within the bounding box
     */
    @Cacheable(
        value = [RedisConfiguration.Companion.FREQUENCY_CACHE],
        key = "'bbox_stops_' + #minLat + '_' + #minLon + '_' + #maxLat + '_' + #maxLon"
    )
    fun getStopsInBoundingBox(
        minLat: Double,
        minLon: Double,
        maxLat: Double,
        maxLon: Double
    ): List<StopSummaryDTO> =
        stopRepository.findByBoundingBox(minLat, minLon, maxLat, maxLon)
            .map { StopSummaryDTO.fromDomain(it) }

    /**
     * Get all stations (location_type = 1) for a specific feed.
     */
    @Cacheable(value = [RedisConfiguration.Companion.FREQUENCY_CACHE], key = "'feed_stations_' + #feedId + '_' + #pageable.pageNumber")
    fun getStationsByFeed(feedId: FeedId, pageable: Pageable): Page<StopSummaryDTO> =
        stopRepository.findStationsByFeedId(feedId, pageable)
            .map { StopSummaryDTO.fromDomain(it) }

    /**
     * Get stops by location type for a specific feed.
     */
    @Cacheable(
        value = [RedisConfiguration.Companion.FREQUENCY_CACHE],
        key = "'feed_stops_type_' + #feedId + '_' + #locationType + '_' + #pageable.pageNumber"
    )
    fun getStopsByLocationType(feedId: FeedId, locationType: Int, pageable: Pageable): Page<StopSummaryDTO> =
        stopRepository.findByFeedIdAndLocationType(feedId, locationType, pageable)
            .map { StopSummaryDTO.fromDomain(it) }

    /**
     * Count total stops for a feed.
     */
    @Cacheable(value = [RedisConfiguration.Companion.FREQUENCY_CACHE], key = "'feed_stop_count_' + #feedId")
    fun countStopsByFeed(feedId: FeedId): Long =
        stopRepository.countByFeedId(feedId)

    /**
     * Count active stops for a feed.
     */
    @Cacheable(value = [RedisConfiguration.Companion.FREQUENCY_CACHE], key = "'feed_active_stop_count_' + #feedId")
    fun countActiveStopsByFeed(feedId: FeedId): Long =
        stopRepository.countActiveByFeedId(feedId)
}
