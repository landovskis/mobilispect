@file:OptIn(ExperimentalSerializationApi::class)
@file:Suppress("unused") // Deleting any of the unused fields will be break deserialization.

package com.mobilispect.backend.transit_land.agency

import com.mobilispect.backend.TransitLandVersionedFeed
import com.mobilispect.backend.transit_land.Meta
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonNames

@Serializable
internal class TransitLandAgencyResponse(
  val agencies: Collection<TransitLandAgency>,
  val meta: Meta? = null,
)

@Suppress("LongParameterList")
@Serializable
internal class TransitLandAgency(
  @JsonNames("agency_email") val email: String? = null,
  @JsonNames("agency_fare_url") val fareURL: String? = null,
  @JsonNames("agency_id") val agencyID: String?,
  @JsonNames("agency_lang") val language: String? = null,
  @JsonNames("agency_name") val name: String,
  @JsonNames("agency_phone") val phone: String? = null,
  @JsonNames("agency_timezone") val timezone: String? = null,
  @JsonNames("agency_url") val url: String? = null,
  @JsonNames("feed_version") val feed: TransitLandVersionedFeed,
  val geometry: PolygonGeometry? = null,
  val id: Int? = null,
  @JsonNames("onestop_id") val onestopID: String,
  val operator: Operator? = null,
  val places: Array<Place> = emptyArray(),
)

@Serializable
internal class Operator(
  val feeds: Array<OperatorFeed>,
  val name: String,
  @JsonNames("onestop_id") val oneStopID: String,
  @JsonNames("short_name") val shortName: String?,
  val tags: Tags? = null,
)

@Serializable
internal class Tags(
  @JsonNames("twitter_general") val twitter: String? = null,
  @JsonNames("us_ntd_id") val usNtdID: String? = null,
  @JsonNames("wikidata_id") val wikidataID: String? = null,
)

@Serializable
internal class OperatorFeed(
  val id: Int,
  val name: String?,
  @JsonNames("onestop_id") val onestopID: String,
  val spec: String,
)

@Serializable
internal class Place(
  @JsonNames("adm0_name") val country: String,
  @JsonNames("adm1_name") val provinceState: String,
  @JsonNames("city_name") val city: String? = null,
)

@Serializable
internal class PolygonGeometry(val coordinates: Array<Array<Array<Double>>>, val type: String)
