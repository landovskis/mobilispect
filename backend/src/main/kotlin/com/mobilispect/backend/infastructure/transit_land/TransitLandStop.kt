@file:OptIn(ExperimentalSerializationApi::class)
@file:Suppress("unused") // Deleting any of the unused fields will be break deserialization.

package com.mobilispect.backend.infastructure.transit_land

import com.mobilispect.backend.TransitLandVersionedFeed
import com.mobilispect.backend.infastructure.PointGeometry
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonNames

@Serializable
internal class TransitLandStop(
  @JsonNames("feed_version") val feed: TransitLandVersionedFeed,
  val geometry: PointGeometry? = null,
  val id: Int? = null,
  val level: Int? = null,
  @JsonNames("location_type") val locationType: Int? = null,
  @JsonNames("onestop_id") val onestopID: String,
  val parent: ParentStop? = null,
  val place: Place? = null,
  @JsonNames("platform_code") val platformCode: String? = null,
  @JsonNames("stop_code") val stopCode: String? = null,
  @JsonNames("stop_desc") val description: String? = null,
  @JsonNames("stop_id") val stopID: String,
  @JsonNames("stop_name") val name: String,
  @JsonNames("stop_timezone") val timezone: String? = null,
  @JsonNames("stop_url") val url: String? = null,
  @JsonNames("tts_stop_name") val ttsStopName: String? = null,
  @JsonNames("wheelchair_boarding") val wheelchairBoarding: Int? = null,
  @JsonNames("zone_id") val zoneID: String? = null,
)

@Serializable
internal class ParentStop(
  val geometry: PointGeometry? = null,
  val id: Int? = null,
  @JsonNames("stop_id") val stopID: String?,
  @JsonNames("stop_name") val name: String?,
)

@Serializable
class Place(
  @JsonNames("adm0_iso") val countryCode: String,
  @JsonNames("adm0_name") val countryName: String,
  @JsonNames("adm1_iso") val provinceCode: String,
  @JsonNames("adm1_name") val provinceName: String,
)
