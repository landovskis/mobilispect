package com.mobilispect.backend.feed.api

import com.mobilispect.backend.feed.domain.ImportStatus
import com.mobilispect.backend.feed.domain.TriggerType
import java.time.Instant

/** Feed Import Summary DTO */
data class FeedImportDTO(
  val id: String,
  val feedOnestopId: String,
  val administratorId: String?,
  val administratorUsername: String?,
  val triggerType: TriggerType,
  val status: ImportStatus,
  val versionSha1: String?,
  val startedAt: Instant?,
  val completedAt: Instant?,
  val fileSizeBytes: Long?,
  val errorMessage: String?,
  val createdAt: Instant,
  val updatedAt: Instant,
)

data class FeedImportSummaryDTO(
  val id: String,
  val feedOnestopId: String,
  val feedName: String?,
  val regionOnestopId: String?,
  val regionName: String?,
  val status: ImportStatus,
  val triggerType: TriggerType,
  val startedAt: Instant?,
  val completedAt: Instant?,
  val progress: ImportProgressDTO?,
  val currentStep: String? = progress?.currentStep,
)

data class FeedImportDetailDTO(
  val id: String,
  val feedOnestopId: String,
  val administratorId: String?,
  val administratorUsername: String?,
  val triggerType: TriggerType,
  val status: ImportStatus,
  val versionSha1: String?,
  val startedAt: Instant?,
  val completedAt: Instant?,
  val fileSizeBytes: Long?,
  val errorMessage: String?,
  val createdAt: Instant,
  val updatedAt: Instant,
  val feedName: String?,
  val regionName: String?,
  val progress: ImportProgressDTO?,
)

/** Active Imports Response */
data class ActiveImportsResponse(val imports: List<FeedImportSummaryDTO>, val total: Int)

/** Import Progress DTO */
data class ImportProgressDTO(
  val importId: String,
  val feedOnestopId: String,
  val progressPercentage: Int,
  val currentStep: String,
  val currentStepNumber: Int,
  val totalSteps: Int,
  val startedAt: Instant,
  val estimatedTimeRemainingSeconds: Long? = null,
  val processingRate: Double? = null,
)

/** Page Information for Paginated Responses */
data class PageInfo(
  val page: Int,
  val size: Int,
  val totalElements: Int,
  val totalPages: Int,
  val hasNext: Boolean,
  val hasPrevious: Boolean,
)

/** Imports Response with Pagination */
data class ImportsResponse(val imports: List<FeedImportDTO>, val page: PageInfo)
