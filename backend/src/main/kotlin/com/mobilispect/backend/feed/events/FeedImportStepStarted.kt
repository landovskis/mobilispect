package com.mobilispect.backend.feed.events

import com.mobilispect.backend.feed.domain.model.ids.FeedId

class FeedImportStepStarted(
    val feedId: FeedId,
    val step: String
)
