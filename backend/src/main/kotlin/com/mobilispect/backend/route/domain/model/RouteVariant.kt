package com.mobilispect.backend.route.domain.model

import com.mobilispect.backend.route.domain.model.ids.RouteId
import com.mobilispect.backend.route.domain.model.ids.VariantHash
import java.time.Instant

/**
 * Specific service pattern for a route defined by unique stop sequence.
 *
 * Each variant represents a distinct pattern of stops served by trips on a route. The variant ID is
 * a SHA-256 hash of the ordered stop pattern for uniqueness.
 *
 * @property id SHA-256 hash of the stop pattern (64-character hex string)
 * @property routeId Route this variant belongs to
 * @property directionId GTFS direction_id (0 = outbound, 1 = inbound, null = unknown)
 * @property headsign Destination headsign shown to passengers
 * @property stopPattern Pipe-separated ordered stop IDs (e.g., "stop1|stop2|stop3")
 * @property stopNamePattern Pipe-separated ordered stop names (e.g., "Main St|Central Ave|Park
 *   Blvd")
 * @property stopCount Number of stops in the pattern
 * @property firstStopId ID of the first stop in the pattern
 * @property lastStopId ID of the last stop in the pattern
 * @property active Whether this variant is currently active
 * @property firstSeen Timestamp when this variant was first observed
 * @property lastSeen Timestamp when this variant was last observed
 * @property createdAt Record creation timestamp
 * @property updatedAt Record last update timestamp
 */
data class RouteVariant(
  val id: VariantHash,
  val routeId: RouteId,
  val directionId: Int? = null,
  val headsign: String? = null,
  val stopPattern: String,
  val stopNamePattern: String? = null,
  val stopCount: Int,
  val firstStopId: String,
  val lastStopId: String,
  val active: Boolean = true,
  val firstSeen: Instant = Instant.now(),
  val lastSeen: Instant = Instant.now(),
  val createdAt: Instant = Instant.now(),
  val updatedAt: Instant = Instant.now(),
) {
  constructor() :
    this(
      id = VariantHash("0".repeat(64)),
      routeId = RouteId("r-placeholder"),
      stopPattern = "",
      stopCount = 0,
      firstStopId = "",
      lastStopId = "",
      firstSeen = Instant.EPOCH,
      lastSeen = Instant.EPOCH,
      createdAt = Instant.EPOCH,
      updatedAt = Instant.EPOCH,
    )
}
