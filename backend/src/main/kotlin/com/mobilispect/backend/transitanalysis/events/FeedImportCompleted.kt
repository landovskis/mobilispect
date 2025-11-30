package com.mobilispect.backend.transitanalysis.events

import com.mobilispect.backend.feed.model.ids.FeedId

data class FeedImportCompleted(
    val feedId: FeedId,
    val routesProcessed: Int,
    val variantsIdentified: Int,
    val durationMillis: Long
)
