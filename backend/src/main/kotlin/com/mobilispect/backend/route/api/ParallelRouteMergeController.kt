package com.mobilispect.backend.route.api

import com.mobilispect.backend.feed.domain.model.ids.FeedId
import com.mobilispect.backend.route.api.dto.ParallelRouteGroupDTO
import com.mobilispect.backend.route.application.ParallelRouteMergeService
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

/**
 * REST endpoint for identifying and reporting groups of parallel routes.
 *
 * Parallel routes share a geographic corridor within the specified distance threshold. The optional
 * frequency threshold prevents infrequent routes from being included in the analysis.
 */
@RestController
@RequestMapping("/api/v1/routes")
class ParallelRouteMergeController(private val mergeService: ParallelRouteMergeService) {

  /**
   * Find all groups of geographically parallel route variants within a GTFS feed.
   *
   * @param feedId Transitland Onestop feed identifier (e.g. `f-abc-test`)
   * @param distanceThresholdMeters Maximum average Hausdorff distance in metres; variants closer
   *   than this threshold are considered parallel
   * @param minimumFrequencyMinutes Optional ceiling on average headway (minutes); variants with a
   *   longer headway are excluded from the analysis
   * @param frequencyDifferenceThresholdMinutes Optional maximum allowed headway difference
   *   (minutes) between two variants for them to be proposed for merging
   */
  @GetMapping("/parallel")
  fun getParallelRoutes(
    @RequestParam feedId: String,
    @RequestParam distanceThresholdMeters: Double,
    @RequestParam(required = false) minimumFrequencyMinutes: Double?,
    @RequestParam(required = false) frequencyDifferenceThresholdMinutes: Double?,
  ): List<ParallelRouteGroupDTO> =
    mergeService
      .findParallelRouteGroups(
        feedId = FeedId(feedId),
        distanceThresholdMeters = distanceThresholdMeters,
        minimumFrequencyMinutes = minimumFrequencyMinutes,
        frequencyDifferenceThresholdMinutes = frequencyDifferenceThresholdMinutes,
      )
      .map { ParallelRouteGroupDTO.fromDomain(it) }
}
