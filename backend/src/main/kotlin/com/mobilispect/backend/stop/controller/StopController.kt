package com.mobilispect.backend.stop.controller

import com.mobilispect.backend.feed.domain.model.ids.FeedId
import com.mobilispect.backend.stop.api.StopDTO
import com.mobilispect.backend.stop.api.StopSummaryDTO
import com.mobilispect.backend.stop.domain.model.ids.StopId
import com.mobilispect.backend.stop.service.StopQueryService
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.server.ResponseStatusException

/**
 * REST API controller for stop-related queries.
 *
 * Provides endpoints for:
 * - Retrieving individual stops by Onestop ID
 * - Listing stops for a feed (with pagination)
 * - Searching stops within a bounding box
 * - Filtering by location type (stations, platforms, etc.)
 * - Counting stops by feed
 *
 * All endpoints leverage Redis caching via StopQueryService.
 */
@RestController
@RequestMapping("/api/v1/stops")
class StopController(private val stopQueryService: StopQueryService) {

  /**
   * Get a stop by its Onestop ID.
   *
   * @param stopOnestopId Transitland Onestop ID (e.g., s-9q8y-16th~st~mission)
   * @return Stop details with all GTFS attributes
   */
  @GetMapping("/{stopOnestopId}")
  fun getStop(@PathVariable stopOnestopId: String): StopDTO =
    stopQueryService.getStop(StopId(stopOnestopId))
      ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "Stop not found: $stopOnestopId")

  /**
   * Get all stops for a specific feed.
   *
   * @param feedId Feed Onestop ID (e.g., f-9q8y-bart)
   * @param pageable Pagination parameters (page, size, sort)
   * @return Paginated list of stops
   */
  @GetMapping
  fun getStopsByFeed(@RequestParam feedId: String, pageable: Pageable): Page<StopSummaryDTO> =
    stopQueryService.getStopsByFeed(FeedId(feedId), pageable)

  /**
   * Get active stops for a specific feed.
   *
   * @param feedId Feed Onestop ID
   * @param pageable Pagination parameters
   * @return Paginated list of active stops
   */
  @GetMapping("/active")
  fun getActiveStops(@RequestParam feedId: String, pageable: Pageable): Page<StopSummaryDTO> =
    stopQueryService.getActiveStopsByFeed(FeedId(feedId), pageable)

  /**
   * Get stops within a bounding box (for map views).
   *
   * @param minLat Southwest corner latitude
   * @param minLon Southwest corner longitude
   * @param maxLat Northeast corner latitude
   * @param maxLon Northeast corner longitude
   * @return List of stops within the bounding box
   */
  @GetMapping("/bbox")
  fun getStopsInBoundingBox(
    @RequestParam minLat: Double,
    @RequestParam minLon: Double,
    @RequestParam maxLat: Double,
    @RequestParam maxLon: Double,
  ): List<StopSummaryDTO> {
    // Validate bounding box coordinates
    require(minLat >= -90 && minLat <= 90) { "minLat must be between -90 and 90" }
    require(maxLat >= -90 && maxLat <= 90) { "maxLat must be between -90 and 90" }
    require(minLon >= -180 && minLon <= 180) { "minLon must be between -180 and 180" }
    require(maxLon >= -180 && maxLon <= 180) { "maxLon must be between -180 and 180" }
    require(minLat <= maxLat) { "minLat must be less than or equal to maxLat" }
    require(minLon <= maxLon) { "minLon must be less than or equal to maxLon" }

    return stopQueryService.getStopsInBoundingBox(minLat, minLon, maxLat, maxLon)
  }

  /**
   * Get all stations (location_type = 1) for a specific feed.
   *
   * @param feedId Feed Onestop ID
   * @param pageable Pagination parameters
   * @return Paginated list of stations
   */
  @GetMapping("/stations")
  fun getStations(@RequestParam feedId: String, pageable: Pageable): Page<StopSummaryDTO> =
    stopQueryService.getStationsByFeed(FeedId(feedId), pageable)

  /**
   * Get stops by location type for a specific feed.
   *
   * GTFS location_type values:
   * - 0: Stop/Platform
   * - 1: Station
   * - 2: Entrance/Exit
   * - 3: Generic Node
   * - 4: Boarding Area
   *
   * @param feedId Feed Onestop ID
   * @param locationType GTFS location type (0-4)
   * @param pageable Pagination parameters
   * @return Paginated list of stops
   */
  @GetMapping("/by-type")
  fun getStopsByType(
    @RequestParam feedId: String,
    @RequestParam locationType: Int,
    pageable: Pageable,
  ): Page<StopSummaryDTO> {
    require(locationType in 0..4) { "locationType must be between 0 and 4" }
    return stopQueryService.getStopsByLocationType(FeedId(feedId), locationType, pageable)
  }

  /**
   * Count total stops for a feed.
   *
   * @param feedId Feed Onestop ID
   * @return Total number of stops
   */
  @GetMapping("/count")
  fun countStops(@RequestParam feedId: String): Long =
    stopQueryService.countStopsByFeed(FeedId(feedId))

  /**
   * Count active stops for a feed.
   *
   * @param feedId Feed Onestop ID
   * @return Number of active stops
   */
  @GetMapping("/count/active")
  fun countActiveStops(@RequestParam feedId: String): Long =
    stopQueryService.countActiveStopsByFeed(FeedId(feedId))
}
