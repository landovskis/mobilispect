package com.mobilispect.backend.api

import com.mobilispect.backend.region.domain.RegionImportStatus
import java.time.Instant

/**
 * Result of a bulk import operation for a region.
 *
 * Contains summary counts, parent import tracking ID, and individual results for each feed that was
 * processed.
 */
data class BulkImportResponse(
  /** Unique ID for tracking this region import (parent job). */
  val regionImportId: String?,
  val regionOnestopId: String,
  /** Current status of the region import. */
  val status: RegionImportStatus,
  val totalFeeds: Int,
  val startedCount: Int,
  /** Number of feed imports that completed successfully. */
  val completedCount: Int,
  val failedCount: Int,
  val skippedCount: Int,
  val results: List<FeedImportResult>,
  /** When the region import started (null if pending). */
  val startedAt: Instant?,
)

/** Result of attempting to import a single feed as part of a bulk operation. */
data class FeedImportResult(
  val feedOnestopId: String,
  val feedName: String,
  val status: FeedImportResultStatus,
  val message: String?,
  val importId: String? = null,
)

/**
 * Status of a feed import attempt during bulk import.
 * - STARTED: Import job was successfully launched
 * - FAILED: Import failed to start (e.g., feed not found, validation error)
 * - SKIPPED: Import was skipped (e.g., already running, feed inactive)
 */
enum class FeedImportResultStatus {
  STARTED,
  FAILED,
  SKIPPED,
}
