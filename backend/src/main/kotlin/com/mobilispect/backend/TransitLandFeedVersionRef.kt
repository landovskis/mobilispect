package com.mobilispect.backend

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonNames

@ExperimentalSerializationApi
@Serializable
class TransitLandFeedVersionRef(@JsonNames("sha1") val id: String)
