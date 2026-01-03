package com.mobilispect.backend.api.dto

/**
 * Result of a bulk import operation for a region.
 *
 * Contains summary counts and individual results for each feed that was processed.
 */
data class BulkImportResponse(
  val regionOnestopId: String,
  val totalFeeds: Int,
  val startedCount: Int,
  val failedCount: Int,
  val skippedCount: Int,
  val results: List<FeedImportResult>,
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
