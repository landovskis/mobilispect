package com.mobilispect.backend.feed.events

import com.mobilispect.backend.feed.domain.model.ids.FeedId

class FeedImportStepCompleted(
    val feedId: FeedId,
    val step: String,
)
