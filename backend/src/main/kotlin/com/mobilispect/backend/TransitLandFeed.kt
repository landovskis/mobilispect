@file:Suppress("unused")

package com.mobilispect.backend

import com.mobilispect.backend.transit_land.agency.PolygonGeometry
import kotlinx.serialization.Serializable

@Serializable
internal class TransitLandFeed(
  val authorization: Authorization? = null,
  val feed_state: FeedState? = null,
  val feed_versions: Collection<FeedVersionRecord>,
  val id: Int? = null,
  val languages: Collection<String>? = null,
  val license: License? = null,
  val name: String? = null,
  val onestop_id: String? = null,
  val spec: String,
  val urls: URLS? = null,
)

@Serializable
class URLS(
  val gbfs_auto_discovery: String? = null,
  val mds_provider: String? = null,
  val realtime_alerts: String? = null,
  val realtime_trip_updates: String? = null,
  val realtime_vehicle_positions: String? = null,
  val static_current: String? = null,
  val static_historic: Collection<String>? = null,
  val static_planned: Collection<String>? = null,
)

@Serializable
class License(
  val attribution_instructions: String? = null,
  val attribution_text: String? = null,
  val commercial_use_allowed: String? = null,
  val create_derived_product: String? = null,
  val redistribution_allowed: String? = null,
  val share_alike_optional: String? = null,
  val spdx_identifier: String? = null,
  val url: String? = null,
  val use_without_attribution: String? = null,
)

@Serializable
class FeedVersionRecord(
  val earliest_calendar_date: String,
  val fetched_at: String? = null,
  val id: Int? = null,
  val latest_calendar_date: String,
  val sha1: String,
  val url: String,
)

@Serializable internal class FeedState(val feed_version: FeedStateVersion? = null)

@Serializable
internal class FeedStateVersion(
  val feed_version_gtfs_import: GTFSImport? = null,
  val fetched_at: String? = null,
  val geometry: PolygonGeometry? = null,
  val id: Int? = null,
  val sha1: String? = null,
  val url: String? = null,
)

@Serializable
class GTFSImport(
  val exception_log: String? = null,
  val id: Int? = null,
  val in_progress: Boolean? = null,
  val success: Boolean? = null,
)

@Serializable
class Authorization(
  val info_url: String? = null,
  val param_name: String? = null,
  val type: String? = null,
)
