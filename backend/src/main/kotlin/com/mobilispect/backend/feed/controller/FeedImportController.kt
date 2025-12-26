package com.mobilispect.backend.feed.controller

import com.mobilispect.backend.api.dto.ActiveImportsResponse
import com.mobilispect.backend.api.dto.FeedImportDTO
import com.mobilispect.backend.api.dto.FeedImportDetailDTO
import com.mobilispect.backend.api.dto.FeedImportSummaryDTO
import com.mobilispect.backend.api.dto.ImportProgressDTO
import com.mobilispect.backend.api.dto.ImportRequest
import com.mobilispect.backend.api.dto.ImportResponse
import com.mobilispect.backend.api.dto.ImportsResponse
import com.mobilispect.backend.api.dto.ImportStatus as ImportStatusDto
import com.mobilispect.backend.api.dto.PageInfo
import com.mobilispect.backend.api.dto.TriggerType as TriggerTypeDto
import com.mobilispect.backend.feed.domain.FeedImport
import com.mobilispect.backend.feed.model.ImportStatus
import com.mobilispect.backend.feed.model.ImportTriggerType
import com.mobilispect.backend.feed.model.ids.ImportId
import com.mobilispect.backend.feed.repository.FeedImportRepository
import com.mobilispect.backend.feed.repository.FeedRepository
import com.mobilispect.backend.feed.service.FeedImportService
import com.mobilispect.backend.feed.service.ImportHistoryService
import com.mobilispect.backend.websocket.ProgressTrackingService
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.http.HttpStatus
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.server.ResponseStatusException
import java.util.UUID

/**
 * Unified REST controller for feed import operations and history.
 *
 * Consolidates functionality from ImportController and HistoryController.
 *
 * Endpoints:
 * - POST /api/feeds/feeds/{feedOnestopId}/import - Start feed import
 * - DELETE /api/feeds/imports/{importId} - Cancel import
 * - GET /api/feeds/imports/active - Get active imports
 * - GET /api/feeds/imports/{importId} - Get import detail
 * - GET /api/feeds/imports/{importId}/progress - Get import progress
 * - GET /api/feeds/imports - List all imports
 * - GET /api/feeds/feeds/{feedOnestopId}/imports - List imports for feed
 * - GET /api/feeds/regions/{regionOnestopId}/imports - List imports for region
 * - GET /api/feeds/imports/statistics - Get import statistics
 */
@RestController
@RequestMapping("/api/feeds")
class FeedImportController(
    private val feedImportService: FeedImportService,
    private val feedImportRepository: FeedImportRepository,
    private val feedRepository: FeedRepository,
    private val progressTrackingService: ProgressTrackingService,
    private val importHistoryService: ImportHistoryService
) {
    private val activeStatuses = listOf(ImportStatus.RUNNING, ImportStatus.PENDING)

    // ========== Import Operations ==========

    /**
     * Start a new feed import.
     */
    @PostMapping("/{feedOnestopId}/import")
    fun startImport(
        @PathVariable feedOnestopId: String,
        @RequestBody(required = false) request: ImportRequest?
    ): ImportResponse {
        val import = feedImportService.startImport(
            feedOnestopId = feedOnestopId,
            administratorUsername = null,
            triggerType = ImportTriggerType.MANUAL,
            force = request?.force == true
        )

        val message = "Import started successfully"
        return import.toResponse(message)
    }

    /**
     * Cancel a running import.
     */
    @DeleteMapping("/imports/{importId}")
    fun cancelImport(@PathVariable importId: String): FeedImportDTO {
        val uuid = parseImportId(importId)
        feedImportService.cancelImport(ImportId(uuid))
        val updated = feedImportRepository.findByImportId(ImportId(uuid))
            .orElseThrow { notFound("Import", importId) }
        return updated.toFeedImportDTO()
    }

    // ========== Active Import Tracking ==========

    /**
     * Get all currently active imports.
     */
    @GetMapping("/imports/active")
    @Transactional(readOnly = true)
    fun getActiveImports(): ActiveImportsResponse {
        val imports = feedImportRepository.findAllByStatusIn(activeStatuses)
            .sortedByDescending { it.startedAt ?: it.createdAt }
        val summaries = imports.map { import ->
            val progress = progressTrackingService.getProgress(import.requireIdAsString())?.toDto()
            import.toSummary(progress)
        }
        return ActiveImportsResponse(
            imports = summaries,
            total = summaries.size
        )
    }

    /**
     * Get real-time progress for a specific import.
     */
    @GetMapping("/imports/{importId}/progress")
    @Transactional(readOnly = true)
    fun getImportProgress(@PathVariable importId: String): ImportProgressDTO {
        progressTrackingService.getProgress(importId)?.let { return it.toDto() }

        val uuid = runCatching { UUID.fromString(importId) }
            .getOrElse { throw ResponseStatusException(HttpStatus.NOT_FOUND, "Import not found: $importId") }

        val import = feedImportRepository.findByImportId(ImportId(uuid))
            .orElseThrow { notFound("Import", importId) }

        val progressPercentage = when (import.status) {
            ImportStatus.COMPLETED -> 100
            ImportStatus.FAILED, ImportStatus.CANCELLED -> 0
            else -> 0
        }

        return ImportProgressDTO(
            importId = importId,
            feedOnestopId = import.feedOnestopId,
            progressPercentage = progressPercentage,
            currentStep = when (import.status) {
                ImportStatus.COMPLETED -> "Completed"
                ImportStatus.FAILED -> "Failed"
                ImportStatus.CANCELLED -> "Cancelled"
                else -> "Pending"
            },
            currentStepNumber = if (progressPercentage == 100) 8 else 0,
            totalSteps = 8,
            startedAt = import.startedAt ?: import.createdAt,
            estimatedTimeRemainingSeconds = null,
            processingRate = null
        )
    }

    // ========== Import Details ==========

    /**
     * Get detailed information about a specific import.
     */
    @GetMapping("/imports/{importId}")
    @Transactional(readOnly = true)
    fun getImport(@PathVariable importId: String): FeedImportDetailDTO {
        val uuid = parseImportId(importId)
        val import = feedImportRepository.findByImportId(ImportId(uuid))
            .orElseThrow { notFound("Import", importId) }

        val feed = findFeed(import)
            ?: throw ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Feed missing for import ${import.id}")
        val regionName = feed.regions.firstOrNull()?.name

        val progress = progressTrackingService.getProgress(importId)?.toDto()

        return FeedImportDetailDTO(
            id = import.requireIdAsString(),
            feedOnestopId = feed.feedOnestopId,
            administratorId = import.administrator?.id?.value?.toString(),
            administratorUsername = import.administrator?.username,
            triggerType = import.triggerType.toDto(),
            status = import.status.toDto(),
            versionSha1 = import.versionSha1,
            startedAt = import.startedAt,
            completedAt = import.completedAt,
            fileSizeBytes = import.fileSizeBytes,
            errorMessage = import.errorMessage,
            createdAt = import.createdAt,
            updatedAt = import.updatedAt,
            feedName = feed.name,
            regionName = regionName,
            progress = progress
        )
    }

    // ========== Import History Lists ==========

    /**
     * List all imports with optional filtering.
     */
    @GetMapping("/imports")
    @Transactional(readOnly = true)
    fun listImports(
        @RequestParam(required = false, defaultValue = "0") page: Int,
        @RequestParam(required = false, defaultValue = "20") size: Int,
        @RequestParam(required = false) status: ImportStatusDto?,
        @RequestParam(required = false) triggerType: TriggerTypeDto?
    ): ImportsResponse {
        val pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "startedAt", "createdAt"))

        val statuses = status?.let { listOf(it.toEntity()) }
        val triggerTypes = triggerType?.let { listOf(it.toEntity()) }

        val pageResult = when {
            statuses != null && triggerTypes != null ->
                feedImportRepository.findAllByStatusInAndTriggerTypeIn(statuses, triggerTypes, pageable)
            statuses != null -> feedImportRepository.findAllByStatusIn(statuses, pageable)
            triggerTypes != null -> feedImportRepository.findAllByTriggerTypeIn(triggerTypes, pageable)
            else -> feedImportRepository.findAll(pageable)
        }

        val imports = pageResult.content.map { it.toFeedImportDTO() }

        return ImportsResponse(
            imports = imports,
            page = PageInfo(
                page = pageResult.number,
                size = pageResult.size,
                totalElements = pageResult.totalElements.toInt(),
                totalPages = pageResult.totalPages,
                hasNext = pageResult.hasNext(),
                hasPrevious = pageResult.hasPrevious()
            )
        )
    }

    /**
     * List imports for a specific feed.
     */
    @GetMapping("/{feedOnestopId}/imports")
    @Transactional(readOnly = true)
    fun listImportsForFeed(
        @PathVariable feedOnestopId: String,
        @RequestParam(required = false, defaultValue = "0") page: Int,
        @RequestParam(required = false, defaultValue = "20") size: Int,
        @RequestParam(required = false) status: ImportStatusDto?
    ): ImportsResponse {
        val pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "startedAt", "createdAt"))
        val statuses = status?.let { listOf(it.toEntity()) }

        val pageResult = if (statuses != null) {
            feedImportRepository.findAllByFeedOnestopIdAndStatusInOrderByStartedAtDesc(feedOnestopId, statuses, pageable)
        } else {
            feedImportRepository.findAllByFeedOnestopIdOrderByStartedAtDesc(feedOnestopId, pageable)
        }

        return ImportsResponse(
            imports = pageResult.content.map { it.toFeedImportDTO() },
            page = PageInfo(
                page = pageResult.number,
                size = pageResult.size,
                totalElements = pageResult.totalElements.toInt(),
                totalPages = pageResult.totalPages,
                hasNext = pageResult.hasNext(),
                hasPrevious = pageResult.hasPrevious()
            )
        )
    }

    /**
     * List imports for a specific region.
     */
    @GetMapping("/regions/{regionOnestopId}/imports")
    @Transactional(readOnly = true)
    fun listImportsForRegion(
        @PathVariable regionOnestopId: String,
        @RequestParam(required = false, defaultValue = "0") page: Int,
        @RequestParam(required = false, defaultValue = "20") size: Int,
        @RequestParam(required = false) status: ImportStatusDto?
    ): ImportsResponse {
        val pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "startedAt"))
        val statusFilter = status?.toEntity()

        val pageResult = importHistoryService.getRegionImportHistory(
            regionOnestopId = regionOnestopId,
            status = statusFilter,
            pageable = pageable
        )

        return ImportsResponse(
            imports = pageResult.content.map { it.toFeedImportDTO() },
            page = PageInfo(
                page = pageResult.number,
                size = pageResult.size,
                totalElements = pageResult.totalElements.toInt(),
                totalPages = pageResult.totalPages,
                hasNext = pageResult.hasNext(),
                hasPrevious = pageResult.hasPrevious()
            )
        )
    }

    // ========== Statistics ==========

    /**
     * Get import statistics summary.
     */
    @GetMapping("/imports/statistics")
    @Transactional(readOnly = true)
    fun getStatistics(): StatisticsResponse {
        val stats = importHistoryService.getImportStatistics()
        return StatisticsResponse(
            totalImports = stats.totalImports,
            completedImports = stats.completedImports,
            failedImports = stats.failedImports,
            cancelledImports = stats.cancelledImports,
            runningImports = stats.runningImports,
            manualImports = stats.manualImports,
            automaticImports = stats.automaticImports,
            successRate = stats.successRate
        )
    }

    // ========== Helper Methods ==========

    private fun FeedImport.toResponse(message: String?): ImportResponse {
        val feed = findFeed(this)
            ?: throw ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Feed missing for import $id")
        return ImportResponse(
            id = requireIdAsString(),
            importId = requireIdAsString(),
            feedOnestopId = feed.feedOnestopId,
            administratorId = administrator?.id?.value?.toString(),
            administratorUsername = administrator?.username,
            triggerType = triggerType.toDto(),
            status = status.toDto(),
            versionSha1 = versionSha1,
            startedAt = startedAt ?: createdAt,
            completedAt = completedAt,
            fileSizeBytes = fileSizeBytes,
            errorMessage = errorMessage,
            createdAt = createdAt,
            updatedAt = updatedAt,
            message = message
        )
    }

    private fun FeedImport.toSummary(progress: ImportProgressDTO?): FeedImportSummaryDTO {
        val feed = findFeed(this)
            ?: throw ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Feed missing for import $id")
        val region = feed.regions.firstOrNull()
        return FeedImportSummaryDTO(
            id = requireIdAsString(),
            feedOnestopId = feed.feedOnestopId,
            feedName = feed.name,
            regionOnestopId = region?.regionOnestopId?.value,
            regionName = region?.name ?: "",
            status = status.toDto(),
            triggerType = triggerType.toDto(),
            startedAt = startedAt,
            completedAt = completedAt,
            progress = progress
        )
    }

    private fun FeedImport.toFeedImportDTO(): FeedImportDTO = FeedImportDTO(
        id = requireIdAsString(),
        feedOnestopId = feedOnestopId,
        administratorId = administrator?.id?.value?.toString(),
        administratorUsername = administrator?.username,
        triggerType = triggerType.toDto(),
        status = status.toDto(),
        versionSha1 = versionSha1,
        startedAt = startedAt,
        completedAt = completedAt,
        fileSizeBytes = fileSizeBytes,
        errorMessage = errorMessage,
        createdAt = createdAt,
        updatedAt = updatedAt
    )

    private fun findFeed(import: FeedImport) =
        import.feedOnestopId.takeIf { it.isNotBlank() }
            ?.let { feedRepository.findByFeedOnestopId(it).orElse(null) }

    private fun com.mobilispect.backend.websocket.ImportProgress.toDto(): ImportProgressDTO = ImportProgressDTO(
        importId = importId,
        feedOnestopId = feedOnestopId,
        progressPercentage = progressPercentage,
        currentStep = currentStep,
        currentStepNumber = currentStepNumber,
        totalSteps = totalSteps,
        startedAt = startedAt,
        estimatedTimeRemainingSeconds = estimatedTimeRemainingSeconds,
        processingRate = processingRate
    )

    private fun ImportStatus.toDto(): ImportStatusDto = when (this) {
        ImportStatus.PENDING -> ImportStatusDto.PENDING
        ImportStatus.RUNNING -> ImportStatusDto.RUNNING
        ImportStatus.COMPLETED -> ImportStatusDto.COMPLETED
        ImportStatus.FAILED -> ImportStatusDto.FAILED
        ImportStatus.CANCELLED -> ImportStatusDto.CANCELLED
    }

    private fun ImportTriggerType.toDto(): TriggerTypeDto = when (this) {
        ImportTriggerType.MANUAL -> TriggerTypeDto.MANUAL
        ImportTriggerType.AUTOMATIC -> TriggerTypeDto.AUTOMATIC
    }

    private fun ImportStatusDto.toEntity(): ImportStatus = when (this) {
        ImportStatusDto.PENDING -> ImportStatus.PENDING
        ImportStatusDto.RUNNING -> ImportStatus.RUNNING
        ImportStatusDto.COMPLETED -> ImportStatus.COMPLETED
        ImportStatusDto.FAILED -> ImportStatus.FAILED
        ImportStatusDto.CANCELLED -> ImportStatus.CANCELLED
    }

    private fun TriggerTypeDto.toEntity(): ImportTriggerType = when (this) {
        TriggerTypeDto.MANUAL -> ImportTriggerType.MANUAL
        TriggerTypeDto.AUTOMATIC -> ImportTriggerType.AUTOMATIC
    }

    private fun parseImportId(importId: String): UUID = runCatching { UUID.fromString(importId) }
        .getOrElse { throw notFound("Import", importId) }

    private fun notFound(entity: String, identifier: String) =
        ResponseStatusException(HttpStatus.NOT_FOUND, "$entity not found: $identifier")

    private fun FeedImport.requireIdAsString(): String = requireId().value.toString()

    private fun FeedImport.requireId(): ImportId = id
}

/**
 * Response for import statistics endpoint.
 */
data class StatisticsResponse(
    val totalImports: Int,
    val completedImports: Int,
    val failedImports: Int,
    val cancelledImports: Int,
    val runningImports: Int,
    val manualImports: Int,
    val automaticImports: Int,
    val successRate: Double
)
