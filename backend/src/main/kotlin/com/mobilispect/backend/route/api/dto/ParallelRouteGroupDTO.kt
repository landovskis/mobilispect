package com.mobilispect.backend.route.api.dto

import com.mobilispect.backend.route.domain.model.ParallelRouteGroup

/**
 * REST response body representing one group of geographically parallel route variants.
 *
 * @property routeIds Unique identifiers of all routes in this parallel group
 * @property variantIds SHA-256 hashes of all route variants in the group
 * @property mergedStopSequence Ordered union of all stop patterns in the group
 * @property averageDistanceMeters Average Hausdorff distance (metres) between parallel variants
 */
data class ParallelRouteGroupDTO(
  val routeIds: List<String>,
  val variantIds: List<String>,
  val mergedStopSequence: List<String>,
  val averageDistanceMeters: Double,
) {
  companion object {
    fun fromDomain(group: ParallelRouteGroup): ParallelRouteGroupDTO =
      ParallelRouteGroupDTO(
        routeIds = group.routeIds.map { it.value }.sorted(),
        variantIds = group.variantIds.sorted(),
        mergedStopSequence = group.mergedStopSequence,
        averageDistanceMeters = group.averageDistanceMeters,
      )
  }
}
