package com.mobilispect.backend.route.domain.model

import com.mobilispect.backend.route.RouteId
import java.time.Instant

/**
 * Longest continuous section of stops shared by ALL variants in a given direction.
 *
 * Unlike the general CommonSection which represents pairwise overlaps between variants,
 * RouteCommonSection represents the single longest sequence of consecutive stops that
 * appears in ALL variants of a route traveling in the same direction.
 *
 * This is useful for:
 * - Identifying the "core" or "trunk" section of a route
 * - Understanding which stops are guaranteed to be served by all service patterns
 * - Analyzing route consistency across different service patterns
 *
 * @property id SHA-256 hash of route_id + direction_id + stop_pattern
 * @property routeId Route this common section belongs to
 * @property directionId GTFS direction_id (0 = outbound, 1 = inbound, null = unknown)
 * @property stopPattern Pipe-separated ordered stop IDs (e.g., "stop1|stop2|stop3")
 * @property stopNamePattern Pipe-separated ordered stop names for display
 * @property stopCount Number of stops in the common section
 * @property firstStopId ID of the first stop in the common section
 * @property lastStopId ID of the last stop in the common section
 * @property variantCount Number of variants that share this common section
 * @property createdAt Record creation timestamp
 * @property updatedAt Record last update timestamp
 */
data class RouteCommonSection(
  val id: String,
  val routeId: RouteId,
  val directionId: Int? = null,
  val stopPattern: String,
  val stopNamePattern: String,
  val stopCount: Int,
  val firstStopId: String,
  val lastStopId: String,
  val variantCount: Int,
  val createdAt: Instant = Instant.now(),
  val updatedAt: Instant = Instant.now(),
) {
  constructor() :
    this(
      id = "0".repeat(64),
      routeId = RouteId("r-placeholder"),
      stopPattern = "",
      stopNamePattern = "",
      stopCount = 0,
      firstStopId = "",
      lastStopId = "",
      variantCount = 0,
      createdAt = Instant.EPOCH,
      updatedAt = Instant.EPOCH,
    )
}
