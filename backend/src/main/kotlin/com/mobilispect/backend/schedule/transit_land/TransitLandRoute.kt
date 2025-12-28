@file:OptIn(ExperimentalSerializationApi::class)
@file:Suppress("unused") // Deleting any of the unused fields will be break deserialization.

package com.mobilispect.backend.schedule.transit_land

import com.mobilispect.backend.TransitLandVersionedFeed
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonNames

@Serializable
internal class TransitLandRoute(
  val agency: TransitLandRouteAgency,
  @JsonNames("continuous_drop_off") val continuousDropOff: Boolean? = null,
  @JsonNames("continuous_pickup") val continuousPickup: Boolean? = null,
  @JsonNames("feed_version") val feed: TransitLandVersionedFeed,
  val id: Int?,
  @JsonNames("onestop_id") val onestopID: String,
  @JsonNames("route_color") val colour: String?,
  @JsonNames("route_desc") val description: String?,
  @JsonNames("route_id") val routeID: String,
  @JsonNames("route_long_name") val longName: String,
  @JsonNames("route_short_name") val shortName: String?,
  @JsonNames("route_sort_order") val sortOrder: Int? = null,
  @JsonNames("route_text_color") val textColour: String? = null,
  @JsonNames("route_type") val type: Int,
  @JsonNames("route_url") val routeURL: String? = null,
)

@Serializable
class TransitLandRouteAgency(
  @JsonNames("agency_id") val agencyID: String,
  @JsonNames("agency_name") val name: String?,
  val id: Int?,
  @JsonNames("onestop_id") val oneStopID: String?,
)
