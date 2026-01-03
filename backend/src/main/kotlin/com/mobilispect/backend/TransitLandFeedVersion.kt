package com.mobilispect.backend

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonNames

@ExperimentalSerializationApi
@Serializable
class TransitLandVersionedFeed(
  val feed: TransitLandFeedRef,
  @JsonNames("fetched_at") val fetchedAt: String,
  val id: Int,
  @JsonNames("sha1") val feedVersion: String,
)
