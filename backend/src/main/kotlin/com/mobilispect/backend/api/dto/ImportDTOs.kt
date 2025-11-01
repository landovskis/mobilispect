package com.mobilispect.backend.api.dto

import java.time.Instant

/**
 * Import Request
 *
 * Request to start a feed import.
 */
data class ImportRequest(
    val force: Boolean = false
)

/**
 * Import Response
 *
 * Response when starting an import.
 */
data class ImportResponse(
    val id: String,
    val importId: String,
    val feedOnestopId: String,
    val administratorId: String? = null,
    val administratorUsername: String? = null,
    val triggerType: TriggerType,
    val status: ImportStatus,
    val versionSha1: String? = null,
    val startedAt: Instant,
    val completedAt: Instant? = null,
    val fileSizeBytes: Long? = null,
    val errorMessage: String? = null,
    val createdAt: Instant,
    val updatedAt: Instant,
    val message: String? = null
)

/**
 * Feed Import Summary DTO
 */
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
    val updatedAt: Instant
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
    val currentStep: String? = progress?.currentStep
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
    val recentLogs: List<ImportLogDTO>
)

/**
 * Import Status
 */
enum class ImportStatus {
    PENDING,
    RUNNING,
    COMPLETED,
    FAILED,
    CANCELLED
}

/**
 * Import Trigger Type
 */
enum class TriggerType {
    MANUAL,
    AUTOMATIC
}

enum class LogLevel {
    INFO,
    WARN,
    ERROR
}

/**
 * Active Imports Response
 */
data class ActiveImportsResponse(
    val imports: List<FeedImportSummaryDTO>,
    val total: Int
)

/**
 * Import Progress DTO
 */
data class ImportProgressDTO(
    val importId: String,
    val feedOnestopId: String,
    val progressPercentage: Int,
    val currentStep: String,
    val currentStepNumber: Int,
    val totalSteps: Int,
    val startedAt: Instant,
    val estimatedTimeRemainingSeconds: Long? = null,
    val processingRate: Double? = null
)

data class ImportLogDTO(
    val id: String,
    val importId: String,
    val level: LogLevel,
    val message: String,
    val component: String?,
    val details: Map<String, Any?>?,
    val createdAt: Instant
)

/**
 * Page Information for Paginated Responses
 */
data class PageInfo(
    val page: Int,
    val size: Int,
    val totalElements: Int,
    val totalPages: Int,
    val hasNext: Boolean,
    val hasPrevious: Boolean
)

/**
 * Imports Response with Pagination
 */
data class ImportsResponse(
    val imports: List<FeedImportDTO>,
    val page: PageInfo
)

data class ImportLogsResponse(
    val logs: List<ImportLogDTO>
)
