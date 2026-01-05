package com.mobilispect.backend.region.api

import com.mobilispect.backend.region.RegionId
import java.time.Instant

/**
 * Data Transfer Object for Metropolitan Region.
 *
 * Exposes region data across module boundaries without exposing internal entities. Part of the
 * Region module's public API.
 */
data class RegionDTO(
  val regionId: RegionId,
  val name: String,
  val country: String?,
  val provinceState: String?,
  val autoUpdateEnabled: Boolean,
  val createdAt: Instant,
  val updatedAt: Instant,
)
