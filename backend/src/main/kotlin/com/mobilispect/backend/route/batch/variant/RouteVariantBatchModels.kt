package com.mobilispect.backend.route.batch.variant

import com.mobilispect.backend.feed.api.GTFSStop
import com.mobilispect.backend.feed.api.GTFSTrip
import com.mobilispect.backend.route.domain.model.Route
import com.mobilispect.backend.route.domain.model.RouteVariant

/**
 * Input data for route variant processing.
 *
 * Combines route information with trip data and stop metadata to enable route variant
 * identification through batch processing.
 *
 * @property route The transit route being processed
 * @property trips List of trips belonging to this route
 * @property stopsById Map of stop IDs to stop metadata for coordinate lookups
 * @property routesByFeedLocalId Map of GTFS route IDs to persisted Route entities
 */
data class RouteVariantInput(
  val route: Route,
  val trips: List<GTFSTrip>,
  val stopsById: Map<String, GTFSStop>,
  val routesByFeedLocalId: Map<String, Route>,
)

/**
 * Represents a batch of route variant processing results.
 *
 * This is the output type for route variant processing, containing all variants that were
 * successfully identified from a route's trip patterns.
 *
 * @property variants List of identified route variants with unique stop patterns
 */
data class RouteVariantBatch(val variants: List<RouteVariant>) {
  /** Total number of variants in this batch. */
  val size: Int
    get() = variants.size

  /** Returns true if this batch contains no variants. */
  fun isEmpty(): Boolean = variants.isEmpty()

  /** Returns true if this batch contains at least one variant. */
  fun isNotEmpty(): Boolean = variants.isNotEmpty()

  /** Groups variants by direction ID. */
  fun groupByDirection(): Map<Int?, List<RouteVariant>> {
    return variants.groupBy { it.directionId }
  }

  /** Gets all variant IDs in this batch. */
  fun variantIds(): List<String> {
    return variants.map { it.id.value }
  }

  /** Gets the total number of stops across all variants. */
  fun totalStops(): Int {
    return variants.sumOf { it.stopCount }
  }
}
