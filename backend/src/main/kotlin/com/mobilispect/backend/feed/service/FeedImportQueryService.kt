package com.mobilispect.backend.feed.service

import com.mobilispect.backend.feed.FeedImportSummaryDTO
import com.mobilispect.backend.feed.domain.ImportStatus
import com.mobilispect.backend.feed.domain.TriggerType
import com.mobilispect.backend.feed.model.ImportStatus as ModelImportStatus
import com.mobilispect.backend.feed.repository.FeedImportRepository
import com.mobilispect.backend.feed.repository.FeedRepository
import org.springframework.stereotype.Service

/**
 * Query service for feed imports.
 *
 * Handles read-only operations for feed imports, including fetching active imports with enriched
 * data (feed names, region information).
 */
@Service
class FeedImportQueryService(
  private val feedImportRepository: FeedImportRepository,
  private val feedRepository: FeedRepository,
) {
  /**
   * Get all active (PENDING or RUNNING) imports with feed and region information.
   *
   * Fetches all imports with status PENDING or RUNNING, then enriches each import with the feed
   * name and region information by joining with the feeds and regions tables.
   *
   * @return List of feed import summaries with enriched data
   */
  fun getActiveImports(): List<FeedImportSummaryDTO> {
    // Query for all active imports (PENDING or RUNNING)
    val activeImports =
      feedImportRepository.findAllByStatusIn(
        listOf(ModelImportStatus.PENDING, ModelImportStatus.RUNNING)
      )

    // Map each import to a summary DTO with enriched feed and region data
    return activeImports.map { feedImport ->
      // Fetch the feed entity to get name and region information
      val feed = feedRepository.findByFeedOnestopId(feedImport.feedId).orElse(null)

      // Get the first region (feeds can belong to multiple regions, we take the first)
      val region = feed?.regions?.firstOrNull()

      FeedImportSummaryDTO(
        id = feedImport.id.value.toString(),
        feedOnestopId = feedImport.feedId,
        feedName = feed?.name,
        regionOnestopId = region?.regionOnestopId?.value,
        regionName = region?.name,
        status = ImportStatus.valueOf(feedImport.status.name),
        triggerType = TriggerType.valueOf(feedImport.triggerType.name),
        startedAt = feedImport.startedAt,
        completedAt = feedImport.completedAt,
        progress = null, // Progress is tracked separately via real-time monitoring
        currentStep = null,
      )
    }
  }
}
