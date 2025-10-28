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
    val errorMessage: String?,
    val recordsImported: Long
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

/**
 * Page Information for Paginated Responses
 */
data class PageInfo(
    val page: Int,
    val size: Int,
    val totalElements: Int,
    val totalPages: Int,
    val hasNext: Boolean
)

/**
 * Imports Response with Pagination
 */
data class ImportsResponse(
    val imports: List<FeedImportSummaryDTO>,
    val page: PageInfo
)
