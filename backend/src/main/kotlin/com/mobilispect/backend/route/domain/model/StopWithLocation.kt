package com.mobilispect.backend.route.domain.model

/**
 * Lightweight stop representation carrying geographic coordinates for distance computations.
 *
 * @property stopId GTFS stop_id as stored in [RouteVariant.stopPattern]
 * @property latitude WGS 84 latitude (-90 to +90)
 * @property longitude WGS 84 longitude (-180 to +180)
 */
data class StopWithLocation(
  val stopId: String,
  val latitude: Double,
  val longitude: Double,
)
