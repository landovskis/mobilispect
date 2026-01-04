package com.mobilispect.backend.agency.domain.model

import com.mobilispect.backend.agency.AgencyId
import com.mobilispect.backend.feed.domain.model.ids.FeedId
import java.time.Instant

/**
 * Transit operator providing public transportation service.
 *
 * An agency belongs to a feed, and inherits region membership through the feed's many-to-many
 * relationship with metropolitan regions via the feed_regions junction table.
 *
 * Relationship chain: Agency -> Feed (via feed_onestop_id) -> Regions (via feed_regions table)
 *
 * @property agencyId Unique agency identifier using Transitland Onestop ID format
 *   (o-geohash-name)
 * @property feedId Feed this agency belongs to
 * @property gtfsAgencyId Agency ID from GTFS agency.txt file
 * @property name Agency display name
 * @property website Agency website URL
 * @property phone Agency contact phone number
 * @property lastFeedImport Timestamp of last successful feed import
 * @property active Whether this agency is currently active
 * @property createdAt Record creation timestamp
 * @property updatedAt Record last update timestamp
 */
data class Agency(
  val agencyId: AgencyId,
  val feedId: FeedId,
  val name: String,
  val active: Boolean = true,
  val createdAt: Instant = Instant.now(),
  val updatedAt: Instant = Instant.now(),
)
