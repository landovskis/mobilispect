package com.mobilispect.mobile.data.cloud

import kotlinx.serialization.json.JsonNames

@kotlinx.serialization.Serializable
data class NetworkRoute(
    @JsonNames("uid")
    val id: String,
    val shortName: String,
    val longName: String,
    val agencyID: String,
)
