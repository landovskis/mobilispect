package com.mobilispect.backend.route.domain.model

import com.mobilispect.backend.route.RouteId

/**
 * A group of routes whose corridors are geographically parallel within a distance threshold.
 *
 * Routes in the same group are candidates for ridership-optimisation merging: consolidating them
 * into a single route would eliminate duplicated coverage while potentially increasing frequency.
 *
 * @property routeIds Unique set of route IDs in this group
 * @property variantIds Unique set of variant IDs (SHA-256 hashes) that were detected as parallel
 * @property mergedStopSequence Ordered list of all unique stop IDs that the merged route would
 *   serve, produced by unioning the stop patterns of all variants in the group
 * @property averageDistanceMeters Average directed Hausdorff distance (in metres) between each
 *   pair of variants in the group
 */
data class ParallelRouteGroup(
  val routeIds: Set<RouteId>,
  val variantIds: Set<String>,
  val mergedStopSequence: List<String>,
  val averageDistanceMeters: Double,
)
