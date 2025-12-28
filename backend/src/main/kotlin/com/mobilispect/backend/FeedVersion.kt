package com.mobilispect.backend

import com.mobilispect.backend.feed.domain.model.ids.FeedId
import java.time.LocalDate

data class FeedVersion(
  val uid: String,
  val feedID: FeedId,
  val startsOn: LocalDate,
  val endsOn: LocalDate,
)
