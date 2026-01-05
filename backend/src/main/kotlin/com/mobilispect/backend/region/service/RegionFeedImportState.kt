package com.mobilispect.backend.region.service

import com.mobilispect.backend.feed.domain.model.ids.FeedId

data class RegionFeedImportState(
  val feedId: FeedId,
  val status: RegionFeedImportStatus,
  val currentStep: String? = null,
  val errorMessage: String? = null,
)

enum class RegionFeedImportStatus {
  STARTED,
  IN_PROGRESS,
  COMPLETED,
  FAILED,
}
