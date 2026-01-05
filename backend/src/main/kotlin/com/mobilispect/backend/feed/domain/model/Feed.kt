package com.mobilispect.backend.feed.domain.model

import com.mobilispect.backend.feed.domain.model.ids.FeedId
import com.mobilispect.backend.feed.model.FeedSpecType
import com.mobilispect.backend.feed.model.FeedStatus
import com.mobilispect.backend.region.RegionId
import java.time.Instant

/**
 * Transit data feed containing GTFS or GTFS-RT data.
 *
 * A feed provides transit data for one or more metropolitan regions and contains agencies, routes,
 * stops, and schedule information.
 *
 * This is the domain model representation, separate from the JPA entity. Per ADR 0009, uses FK-only
 * pattern for regions (Set<RegionId>) without entity navigation.
 *
 * @property feedId Unique feed identifier using Transitland Onestop ID format
 * @property name Feed display name
 * @property operatorName Name of the feed operator/provider
 * @property specType Type of feed specification (GTFS or GTFS-RT)
 * @property downloadUrl URL for downloading the feed
 * @property staticFeedUrl Optional static GTFS feed URL
 * @property realtimeFeedUrl Optional realtime GTFS-RT feed URL
 * @property currentVersionSha1 SHA1 hash of current feed version
 * @property status Current feed status (ACTIVE, INACTIVE, ERROR)
 * @property regionIds Set of metropolitan region IDs this feed serves (FK-only pattern)
 * @property lastCheckedAt Timestamp of last feed availability check
 * @property lastUpdatedAt Timestamp of last feed content update
 * @property lastDiscoveredAt Timestamp when feed was discovered
 * @property createdAt Record creation timestamp
 * @property updatedAt Record last modification timestamp
 */
data class Feed(
  val feedId: FeedId,
  val name: String,
  val operatorName: String? = null,
  val specType: FeedSpecType,
  val downloadUrl: String,
  val staticFeedUrl: String? = null,
  val realtimeFeedUrl: String? = null,
  val currentVersionSha1: String? = null,
  val status: FeedStatus,
  val regionIds: Set<RegionId> = emptySet(),
  val lastCheckedAt: Instant? = null,
  val lastUpdatedAt: Instant? = null,
  val lastDiscoveredAt: Instant? = null,
  val createdAt: Instant = Instant.now(),
  val updatedAt: Instant = Instant.now(),
)
