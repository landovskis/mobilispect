package com.mobilispect.backend

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

@Serializable
class TransitLandOperatorResponse(
    val operators: Collection<TransitLandOperator>,
    val meta: TransitLandMeta? = null
)

@Serializable
class TransitLandOperator(
    val onestop_id: String? = null,
    val name: String? = null,
    val agencies: Collection<TransitLandAgency>? = null,
    val feeds: Collection<TransitLandOperatorFeed>? = null
)

@Serializable
class TransitLandAgency(
    val agency_id: String? = null,
    val agency_name: String? = null,
    val places: Collection<TransitLandPlace>? = null,
    val geometry: JsonElement? = null  // GeoJSON geometry, not used but needed for deserialization
)

@Serializable
class TransitLandPlace(
    val adm0_name: String? = null,
    val adm1_name: String? = null,
    val city_name: String? = null
)

@Serializable
class TransitLandOperatorFeed(
    val onestop_id: String? = null,
    val spec: String? = null
)
