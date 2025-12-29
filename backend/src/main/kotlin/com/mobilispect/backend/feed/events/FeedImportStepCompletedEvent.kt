package com.mobilispect.backend.feed.events

import com.mobilispect.backend.feed.domain.model.ids.FeedId
import com.mobilispect.backend.feed.model.ids.ImportId

class FeedImportStepCompletedEvent(val feedId: FeedId, val step: String, val importId: ImportId)
