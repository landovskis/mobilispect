package com.mobilispect.backend.route.domain.model

/**
 * A [RouteVariant] paired with the geographic locations of its stops.
 *
 * Used by [com.mobilispect.backend.route.domain.service.ParallelRouteDetectionService] to compute
 * geographic distances between variants without requiring cross-module repository access inside the
 * domain layer.
 *
 * @property variant The route variant
 * @property stops Ordered list of stops with their coordinates (same order as the variant's
 *   stopPattern)
 */
data class RouteVariantWithStops(
  val variant: RouteVariant,
  val stops: List<StopWithLocation>,
)
