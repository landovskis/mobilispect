package com.mobilispect.backend

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonNames
import com.mobilispect.backend.feed.model.ids.FeedId
import java.time.LocalDate

data class FeedVersion(
    val uid: String, val feedID: FeedId, val startsOn: LocalDate, val endsOn: LocalDate
)
