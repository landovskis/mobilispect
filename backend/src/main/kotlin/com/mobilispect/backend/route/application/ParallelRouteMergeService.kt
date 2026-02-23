package com.mobilispect.backend.route.application

import com.mobilispect.backend.feed.domain.model.ids.FeedId
import com.mobilispect.backend.route.domain.model.ParallelRouteGroup
import com.mobilispect.backend.route.domain.model.RouteVariantWithStops
import com.mobilispect.backend.route.domain.model.StopWithLocation
import com.mobilispect.backend.route.domain.repository.FrequencyRepository
import com.mobilispect.backend.route.domain.repository.RouteVariantRepository
import com.mobilispect.backend.route.domain.service.ParallelRouteDetectionService
import com.mobilispect.backend.stop.api.StopQueryApi
import org.springframework.stereotype.Service

/**
 * Application service that identifies and reports groups of parallel routes within a feed.
 *
 * Two route variants are considered parallel when the average Hausdorff distance between their stop
 * corridors is at or below [distanceThresholdMeters]. An optional [minimumFrequencyMinutes]
 * parameter filters out routes whose average headway exceeds the specified value, so only
 * sufficiently-frequent routes are considered for merging.
 */
@Service
class ParallelRouteMergeService(
  private val variantRepository: RouteVariantRepository,
  private val frequencyRepository: FrequencyRepository,
  private val stopQueryApi: StopQueryApi,
  private val detectionService: ParallelRouteDetectionService,
) {

  /**
   * Find all groups of parallel route variants in the given feed.
   *
   * @param feedId The feed to analyse
   * @param distanceThresholdMeters Maximum average Hausdorff distance (metres) between two variants
   *   for them to be considered parallel
   * @param minimumFrequencyMinutes Optional headway ceiling (minutes). When provided, variants
   *   without a frequency record or with an average headway exceeding this value are excluded from
   *   the analysis.
   * @return List of parallel route groups; empty when none found
   */
  fun findParallelRouteGroups(
    feedId: FeedId,
    distanceThresholdMeters: Double,
    minimumFrequencyMinutes: Double? = null,
  ): List<ParallelRouteGroup> {
    // ── 1. Load stops for the feed, indexed by GTFS stop ID ──────────────────
    val stopsByGtfsId = stopQueryApi.findStopsByFeed(feedId).associateBy { it.gtfsStopId }

    // ── 2. Load variants that belong to this feed ─────────────────────────────
    val feedPrefix = "r-${feedId.value}"
    val feedVariants =
      variantRepository.findAll().filter { v -> v.routeId.value.startsWith(feedPrefix) }

    // ── 3. Apply frequency filter (if requested) ──────────────────────────────
    val eligibleVariants =
      if (minimumFrequencyMinutes != null) {
        feedVariants.filter { v ->
          val recent = frequencyRepository.findRecentByVariant(v.id.value, 1)
          recent.isNotEmpty() &&
            recent[0].averageHeadway != null &&
            recent[0].averageHeadway!! <= minimumFrequencyMinutes
        }
      } else {
        feedVariants
      }

    // ── 4. Enrich variants with stop coordinates ──────────────────────────────
    val variantsWithStops =
      eligibleVariants.map { v ->
        val gtfsStopIds = v.stopPattern.split("|").filter { it.isNotEmpty() }
        val stops =
          gtfsStopIds.mapNotNull { id ->
            stopsByGtfsId[id]?.let { dto -> StopWithLocation(dto.gtfsStopId, dto.latitude, dto.longitude) }
          }
        RouteVariantWithStops(v, stops)
      }

    // ── 5. Detect and return parallel groups ──────────────────────────────────
    return detectionService.detectParallelRoutes(variantsWithStops, distanceThresholdMeters)
  }
}
