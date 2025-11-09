package com.mobilispect.backend.feed.controller

import com.mobilispect.backend.api.dto.FeedImportDTO
import com.mobilispect.backend.api.dto.FeedImportDetailDTO
import com.mobilispect.backend.api.dto.ImportLogDTO
import com.mobilispect.backend.api.dto.ImportStatus as ImportStatusDto
import com.mobilispect.backend.api.dto.PageInfo
import com.mobilispect.backend.api.dto.TriggerType as TriggerTypeDto
import com.mobilispect.backend.feed.model.FeedImport
import com.mobilispect.backend.feed.model.ImportStatus
import com.mobilispect.backend.feed.model.ImportTriggerType
import com.mobilispect.backend.feed.repository.ImportLogRepository
import com.mobilispect.backend.feed.service.ImportHistoryService
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.http.HttpStatus
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.server.ResponseStatusException
import java.util.UUID

/**
 * REST controller for import history endpoints.
 *
 * Task T051: Create HistoryController for history API endpoints
 * Per User Story 3: Administrators can view comprehensive history of feed imports
 *
 * Endpoints:
 * - GET /api/feeds/history - Get paginated import history
 * - GET /api/feeds/history/{importId} - Get detailed import information
 * - GET /api/feeds/history/feeds/{feedOnestopId} - Get feed-specific history
 * - GET /api/feeds/history/regions/{regionOnestopId} - Get region-specific history
 * - GET /api/feeds/history/statistics - Get import statistics
 */
@RestController
@RequestMapping("/api/feeds/history")
class HistoryController(
    private val importHistoryService: ImportHistoryService,
    private val importLogRepository: ImportLogRepository
) {

    /**
     * Get paginated import history with optional filtering.
     */
    @GetMapping
    @Transactional(readOnly = true)
    fun getImportHistory(
        @RequestParam(required = false, defaultValue = "0") page: Int,
        @RequestParam(required = false, defaultValue = "20") size: Int,
        @RequestParam(required = false) status: ImportStatusDto?,
        @RequestParam(required = false) triggerType: TriggerTypeDto?
    ): HistoryResponse {
        val pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "startedAt", "createdAt"))

        val statusFilter = status?.toEntity()
        val triggerTypeFilter = triggerType?.toEntity()

        val pageResult = importHistoryService.getImportHistory(
            status = statusFilter,
            triggerType = triggerTypeFilter,
            pageable = pageable
        )

        val imports = pageResult.content.map { it.toDTO() }

        return HistoryResponse(
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
     * Get detailed information about a specific import.
     */
    @GetMapping("/{importId}")
    @Transactional(readOnly = true)
    fun getImportDetail(@PathVariable importId: String): FeedImportDetailDTO {
        val uuid = parseImportId(importId)
        val import = importHistoryService.getImportDetail(uuid)
            ?: throw notFound("Import", importId)

        val feed = import.feed
            ?: throw ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Feed missing for import")

        val logs = importLogRepository.findAllByFeedImportIdOrderByCreatedAtAsc(uuid)
            .map { log ->
                ImportLogDTO(
                    id = log.id?.toString() ?: "",
                    importId = importId,
                    level = log.level.toDto(),
                    message = log.message,
                    component = log.component,
                    details = null, // Could parse JSON if needed
                    createdAt = log.createdAt
                )
            }

        return FeedImportDetailDTO(
            id = import.requireIdAsString(),
            feedOnestopId = feed.feedOnestopId,
            administratorId = import.administrator?.id?.toString(),
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
            regionName = feed.regions.firstOrNull()?.name,
            progress = null,
            recentLogs = logs
        )
    }

    /**
     * Get import history for a specific feed.
     */
    @GetMapping("/feeds/{feedOnestopId}")
    @Transactional(readOnly = true)
    fun getFeedHistory(
        @PathVariable feedOnestopId: String,
        @RequestParam(required = false, defaultValue = "0") page: Int,
        @RequestParam(required = false, defaultValue = "20") size: Int,
        @RequestParam(required = false) status: ImportStatusDto?
    ): HistoryResponse {
        val pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "startedAt"))
        val statusFilter = status?.toEntity()

        val pageResult = importHistoryService.getFeedImportHistory(
            feedOnestopId = feedOnestopId,
            status = statusFilter,
            pageable = pageable
        )

        return HistoryResponse(
            imports = pageResult.content.map { it.toDTO() },
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
     * Get import history for a region.
     */
    @GetMapping("/regions/{regionOnestopId}")
    @Transactional(readOnly = true)
    fun getRegionHistory(
        @PathVariable regionOnestopId: String,
        @RequestParam(required = false, defaultValue = "0") page: Int,
        @RequestParam(required = false, defaultValue = "20") size: Int,
        @RequestParam(required = false) status: ImportStatusDto?
    ): HistoryResponse {
        val pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "startedAt"))
        val statusFilter = status?.toEntity()

        val pageResult = importHistoryService.getRegionImportHistory(
            regionOnestopId = regionOnestopId,
            status = statusFilter,
            pageable = pageable
        )

        return HistoryResponse(
            imports = pageResult.content.map { it.toDTO() },
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
     * Get import statistics summary.
     */
    @GetMapping("/statistics")
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

    // Helper methods
    private fun FeedImport.toDTO(): FeedImportDTO = FeedImportDTO(
        id = requireIdAsString(),
        feedOnestopId = feed?.feedOnestopId ?: "",
        administratorId = administrator?.id?.toString(),
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

    private fun com.mobilispect.backend.feed.model.LogLevel.toDto(): com.mobilispect.backend.api.dto.LogLevel = when (this) {
        com.mobilispect.backend.feed.model.LogLevel.INFO -> com.mobilispect.backend.api.dto.LogLevel.INFO
        com.mobilispect.backend.feed.model.LogLevel.WARN -> com.mobilispect.backend.api.dto.LogLevel.WARN
        com.mobilispect.backend.feed.model.LogLevel.ERROR -> com.mobilispect.backend.api.dto.LogLevel.ERROR
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

    private fun FeedImport.requireIdAsString(): String = requireId().toString()

    private fun FeedImport.requireId(): UUID = id
        ?: throw ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Import identifier is not set")
}

/**
 * Response for import history list endpoints.
 */
data class HistoryResponse(
    val imports: List<FeedImportDTO>,
    val page: PageInfo
)

/**
 * Response for statistics endpoint.
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
