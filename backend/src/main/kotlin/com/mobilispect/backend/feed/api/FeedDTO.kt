package com.mobilispect.backend.feed.api

import com.mobilispect.backend.feed.domain.model.ids.FeedId
import com.mobilispect.backend.feed.model.FeedSpecType
import com.mobilispect.backend.feed.model.FeedStatus
import com.mobilispect.backend.region.RegionId
import java.time.Instant

/**
 * Data Transfer Object for Feed.
 *
 * Exposes feed data across module boundaries without exposing internal entities. Part of the Feed
 * module's public API.
 */
data class FeedDTO(
  val feedId: FeedId,
  val name: String?,
  val specType: FeedSpecType,
  val downloadUrl: String,
  val currentVersionSha1: String?,
  val status: FeedStatus,
  val regionIds: Set<RegionId>,
  val lastCheckedAt: Instant?,
  val lastUpdatedAt: Instant?,
  val lastDiscoveredAt: Instant?,
  val createdAt: Instant,
  val updatedAt: Instant,
)
