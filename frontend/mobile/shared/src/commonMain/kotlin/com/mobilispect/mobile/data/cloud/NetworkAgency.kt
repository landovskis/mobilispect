package com.mobilispect.mobile.data.cloud

import kotlinx.serialization.json.JsonNames

@kotlinx.serialization.Serializable
class NetworkAgency(
    @JsonNames("uid")
    val id: String,
    val name: String,
)
