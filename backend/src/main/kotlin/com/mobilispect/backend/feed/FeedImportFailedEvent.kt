package com.mobilispect.backend.feed.events

import com.mobilispect.backend.feed.domain.model.ids.FeedId

class FeedImportFailedEvent(val feedId: FeedId, val step: String, val message: String)
