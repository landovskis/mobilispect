package com.mobilispect.backend

import kotlinx.serialization.Serializable

@Serializable
internal class TransitLandFeedResponse(
    val feeds: Collection<TransitLandFeed>,
    val meta: TransitLandMeta? = null
)

@Serializable
class TransitLandMeta(
    val after: Int? = null,
    val next: String? = null
)
