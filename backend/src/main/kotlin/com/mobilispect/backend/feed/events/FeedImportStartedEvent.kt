package com.mobilispect.backend.feed.events

import com.mobilispect.backend.feed.domain.model.ids.FeedId
import com.mobilispect.backend.feed.model.ids.ImportId

class FeedImportStartedEvent(val feedId: FeedId, val importId: ImportId)
