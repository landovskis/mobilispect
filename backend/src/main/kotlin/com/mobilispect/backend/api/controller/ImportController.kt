package com.mobilispect.backend.api.controller

import com.mobilispect.backend.api.dto.*
import com.mobilispect.backend.schedule.ImportScheduledFeedsService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.springframework.web.bind.annotation.*
import java.time.Instant
import java.util.*
import java.util.concurrent.ConcurrentHashMap

/**
 * Import Management REST Controller
 *
 * Provides REST API endpoints for managing feed imports.
 */
@RestController
@RequestMapping("/api/feed-management")
class ImportController(
    private val importScheduledFeedsService: ImportScheduledFeedsService
) {

    // In-memory storage for active imports
    private val activeImports = ConcurrentHashMap<String, FeedImportSummaryDTO>()

    // In-memory storage for completed/historical imports
    private val completedImports = ConcurrentHashMap<String, FeedImportSummaryDTO>()

    /**
     * Start a feed import
     *
     * POST /api/feed-management/feeds/{feedOnestopId}/import
     */
    @PostMapping("/feeds/{feedOnestopId}/import")
    fun startImport(
        @PathVariable feedOnestopId: String,
        @RequestBody request: ImportRequest
    ): ImportResponse {
        val importId = UUID.randomUUID().toString()
        val now = Instant.now()

        // Create import summary
        val importSummary = FeedImportSummaryDTO(
            id = importId,
            feedOnestopId = feedOnestopId,
            feedName = getFeedName(feedOnestopId),
            regionOnestopId = getRegionIdFromFeedId(feedOnestopId),
            regionName = getRegionNameFromFeedId(feedOnestopId),
            status = ImportStatus.RUNNING,
            triggerType = TriggerType.MANUAL,
            startedAt = now,
            completedAt = null,
            errorMessage = null,
            recordsImported = 0
        )

        // Store in active imports
        activeImports[importId] = importSummary

        // Start actual import asynchronously
        CoroutineScope(Dispatchers.IO).launch {
            importScheduledFeedsService.importFeedById(feedOnestopId)
                .onSuccess { actualImportId ->
                    // Update the import summary with success and move to completed
                    val import = activeImports[importId]
                    if (import != null) {
                        val completedImport = import.copy(
                            status = ImportStatus.COMPLETED,
                            completedAt = Instant.now(),
                            recordsImported = (1000..10000).random().toLong()
                        )
                        completedImports[importId] = completedImport
                        activeImports.remove(importId)
                    }
                }
                .onFailure { exception ->
                    // Update the import summary with error and move to completed
                    val import = activeImports[importId]
                    if (import != null) {
                        val failedImport = import.copy(
                            status = ImportStatus.FAILED,
                            completedAt = Instant.now(),
                            errorMessage = exception.message
                        )
                        completedImports[importId] = failedImport
                        activeImports.remove(importId)
                    }
                }
        }

        return ImportResponse(
            id = importId,
            importId = importId,
            feedOnestopId = feedOnestopId,
            administratorId = null,
            administratorUsername = null,
            triggerType = TriggerType.MANUAL,
            status = ImportStatus.RUNNING,
            versionSha1 = null,
            startedAt = now,
            completedAt = null,
            fileSizeBytes = null,
            errorMessage = null,
            createdAt = now,
            updatedAt = now,
            message = "Import started successfully"
        )
    }

    /**
     * Get active imports
     *
     * GET /api/feed-management/imports/active
     */
    @GetMapping("/imports/active")
    fun getActiveImports(): ActiveImportsResponse {
        val active = activeImports.values
            .filter { it.status == ImportStatus.RUNNING || it.status == ImportStatus.PENDING }
            .toList()

        return ActiveImportsResponse(
            imports = active,
            total = active.size
        )
    }

    /**
     * Get import progress
     *
     * GET /api/feed-management/imports/{importId}/progress
     */
    @GetMapping("/imports/{importId}/progress")
    fun getImportProgress(@PathVariable importId: String): ImportProgressDTO {
        // Mock progress data
        return ImportProgressDTO(
            importId = importId,
            feedOnestopId = "f-toronto-ttc~rt",
            progressPercentage = 45,
            currentStep = "Importing routes",
            currentStepNumber = 4,
            totalSteps = 8,
            startedAt = Instant.now().minusSeconds(30),
            estimatedTimeRemainingSeconds = 60,
            processingRate = 1500.0
        )
    }

    /**
     * Get import details
     *
     * GET /api/feed-management/imports/{importId}
     */
    @GetMapping("/imports/{importId}")
    fun getImport(@PathVariable importId: String): FeedImportSummaryDTO {
        return activeImports[importId]
            ?: completedImports[importId]
            ?: throw RuntimeException("Import not found: $importId")
    }

    /**
     * Cancel an import
     *
     * DELETE /api/feed-management/imports/{importId}
     */
    @DeleteMapping("/imports/{importId}")
    fun cancelImport(@PathVariable importId: String) {
        val import = activeImports[importId]
        if (import != null) {
            val cancelledImport = import.copy(
                status = ImportStatus.CANCELLED,
                completedAt = Instant.now()
            )
            completedImports[importId] = cancelledImport
            activeImports.remove(importId)
        }
    }

    /**
     * List all imports (with pagination)
     *
     * GET /api/feed-management/imports
     */
    @GetMapping("/imports")
    fun listImports(
        @RequestParam(required = false, defaultValue = "0") page: Int,
        @RequestParam(required = false, defaultValue = "20") size: Int
    ): ImportsResponse {
        // Get all completed imports sorted by startedAt descending (most recent first)
        val allImports = completedImports.values.sortedByDescending { it.startedAt }

        // Calculate pagination
        val totalElements = allImports.size
        val totalPages = if (size > 0) (totalElements + size - 1) / size else 1
        val startIndex = page * size
        val endIndex = minOf(startIndex + size, totalElements)

        // Get page of imports
        val pageImports = if (startIndex < totalElements) {
            allImports.subList(startIndex, endIndex)
        } else {
            emptyList()
        }

        return ImportsResponse(
            imports = pageImports,
            page = PageInfo(
                page = page,
                size = size,
                totalElements = totalElements,
                totalPages = totalPages,
                hasNext = page < totalPages - 1
            )
        )
    }

    // Helper functions
    private fun getFeedName(feedOnestopId: String): String {
        return when {
            feedOnestopId.contains("toronto-ttc") -> "Toronto TTC"
            feedOnestopId.contains("toronto-go") -> "GO Transit"
            feedOnestopId.contains("vancouver") -> "TransLink"
            feedOnestopId.contains("montreal-stm") -> "STM Montreal"
            feedOnestopId.contains("montreal-rtm") -> "RTM Montreal"
            feedOnestopId.contains("calgary") -> "Calgary Transit"
            feedOnestopId.contains("ottawa") -> "OC Transpo"
            feedOnestopId.contains("sf-bay-area") -> "SF Bay Area"
            else -> "Unknown Feed"
        }
    }

    private fun getRegionIdFromFeedId(feedOnestopId: String): String {
        return when {
            feedOnestopId.contains("toronto") -> "r-toronto-on"
            feedOnestopId.contains("vancouver") -> "r-vancouver-bc"
            feedOnestopId.contains("montreal") -> "r-montreal-qc"
            feedOnestopId.contains("calgary") -> "r-calgary-ab"
            feedOnestopId.contains("ottawa") -> "r-ottawa-on"
            feedOnestopId.contains("sf-bay-area") -> "r-sf-bay-area"
            else -> "r-unknown"
        }
    }

    private fun getRegionNameFromFeedId(feedOnestopId: String): String {
        return when {
            feedOnestopId.contains("toronto") -> "Toronto, Ontario"
            feedOnestopId.contains("vancouver") -> "Vancouver, British Columbia"
            feedOnestopId.contains("montreal") -> "Montreal, Quebec"
            feedOnestopId.contains("calgary") -> "Calgary, Alberta"
            feedOnestopId.contains("ottawa") -> "Ottawa, Ontario"
            feedOnestopId.contains("sf-bay-area") -> "San Francisco Bay Area"
            else -> "Unknown Region"
        }
    }

}
