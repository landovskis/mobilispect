package com.mobilispect.backend.region.domain

import com.mobilispect.backend.feed.model.ImportTriggerType
import com.mobilispect.backend.feed.model.ImportTriggerTypeConverter
import jakarta.persistence.AttributeOverride
import jakarta.persistence.CascadeType
import jakarta.persistence.Column
import jakarta.persistence.Convert
import jakarta.persistence.EmbeddedId
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.OneToMany
import jakarta.persistence.PrePersist
import jakarta.persistence.PreUpdate
import jakarta.persistence.Table
import java.time.Instant
import org.hibernate.annotations.ColumnTransformer

/**
 * Entity representing a region-level bulk import operation. Acts as the parent job that
 * orchestrates multiple child feed import jobs.
 *
 * Per constitutional modular monolith requirements, this entity:
 * - Lives in the region module's domain
 * - References feed imports only via the junction table (RegionImportFeed)
 * - Does not directly access feed module internals
 */
@Entity
@Table(name = "region_imports")
class RegionImport(
  @EmbeddedId
  @AttributeOverride(
    name = "value",
    column = Column(name = "id", nullable = false, updatable = false, columnDefinition = "uuid"),
  )
  var id: RegionImportId = RegionImportId.random(),
  @Column(name = "region_onestop_id", nullable = false, length = 512)
  var regionOnestopId: String = "",
  @Convert(converter = ImportTriggerTypeConverter::class)
  @ColumnTransformer(read = "trigger_type::text", write = "?::import_trigger_type")
  @Column(
    name = "trigger_type",
    nullable = false,
    length = 16,
    columnDefinition = "import_trigger_type",
  )
  var triggerType: ImportTriggerType = ImportTriggerType.MANUAL,
  @Convert(converter = RegionImportStatusConverter::class)
  @ColumnTransformer(read = "status::text", write = "?::region_import_status")
  @Column(name = "status", nullable = false, length = 20, columnDefinition = "region_import_status")
  var status: RegionImportStatus = RegionImportStatus.PENDING,
  @Column(name = "parent_job_execution_id") var parentJobExecutionId: Long? = null,
  @Column(name = "total_feeds", nullable = false) var totalFeeds: Int = 0,
  @Column(name = "started_count", nullable = false) var startedCount: Int = 0,
  @Column(name = "completed_count", nullable = false) var completedCount: Int = 0,
  @Column(name = "failed_count", nullable = false) var failedCount: Int = 0,
  @Column(name = "skipped_count", nullable = false) var skippedCount: Int = 0,
  @Column(name = "started_at") var startedAt: Instant? = null,
  @Column(name = "completed_at") var completedAt: Instant? = null,
  @Column(name = "error_message", columnDefinition = "text") var errorMessage: String? = null,
  @Column(name = "created_at", nullable = false) var createdAt: Instant = Instant.now(),
  @Column(name = "updated_at", nullable = false) var updatedAt: Instant = Instant.now(),
  @OneToMany(
    mappedBy = "regionImport",
    cascade = [CascadeType.ALL],
    orphanRemoval = true,
    fetch = FetchType.LAZY,
  )
  var feeds: MutableList<RegionImportFeed> = mutableListOf(),
) {
  constructor() :
    this(
      id = RegionImportId(),
      regionOnestopId = "",
      triggerType = ImportTriggerType.MANUAL,
      status = RegionImportStatus.PENDING,
      parentJobExecutionId = null,
      totalFeeds = 0,
      startedCount = 0,
      completedCount = 0,
      failedCount = 0,
      skippedCount = 0,
      startedAt = null,
      completedAt = null,
      errorMessage = null,
      createdAt = Instant.now(),
      updatedAt = Instant.now(),
      feeds = mutableListOf(),
    )

  @PrePersist
  fun onCreate() {
    val now = Instant.now()
    createdAt = now
    updatedAt = now
  }

  @PreUpdate
  fun onUpdate() {
    updatedAt = Instant.now()
  }

  /** Marks the import as running and sets the start time. */
  fun start(jobExecutionId: Long? = null) {
    status = RegionImportStatus.RUNNING
    startedAt = Instant.now()
    parentJobExecutionId = jobExecutionId
  }

  /** Increments the started count when a child feed import starts. */
  fun markFeedStarted() {
    startedCount++
  }

  /** Increments the completed count when a child feed import completes successfully. */
  fun markFeedCompleted() {
    completedCount++
    checkAndFinalizeIfComplete()
  }

  /** Increments the failed count when a child feed import fails. */
  fun markFeedFailed() {
    failedCount++
    checkAndFinalizeIfComplete()
  }

  /** Increments the skipped count when a feed is skipped (e.g., already importing). */
  fun markFeedSkipped() {
    skippedCount++
    checkAndFinalizeIfComplete()
  }

  /**
   * Checks if all feeds have been processed and finalizes the import status. Sets status to:
   * - COMPLETED if all feeds succeeded
   * - PARTIAL_SUCCESS if some succeeded and some failed
   * - FAILED if all feeds failed
   */
  private fun checkAndFinalizeIfComplete() {
    val processedCount = completedCount + failedCount + skippedCount
    if (processedCount >= totalFeeds) {
      finalize()
    }
  }

  /**
   * Explicitly finalizes the import and determines the terminal status. Called by
   * checkAndFinalizeIfComplete or can be called directly to force completion.
   */
  fun finalize() {
    completedAt = Instant.now()
    status =
      when {
        failedCount == 0 && completedCount > 0 -> RegionImportStatus.COMPLETED
        completedCount > 0 && failedCount > 0 -> RegionImportStatus.PARTIAL_SUCCESS
        failedCount > 0 && completedCount == 0 -> RegionImportStatus.FAILED
        else -> RegionImportStatus.COMPLETED // Edge case: all skipped
      }
  }

  /** Marks the import as failed with an error message. */
  fun fail(message: String) {
    status = RegionImportStatus.FAILED
    completedAt = Instant.now()
    errorMessage = message
  }

  /** Marks the import as cancelled. */
  fun cancel() {
    status = RegionImportStatus.CANCELLED
    completedAt = Instant.now()
  }

  /** Adds a feed import to this region import. */
  fun addFeed(feedImportId: java.util.UUID, sequenceNumber: Int): RegionImportFeed {
    val feed =
      RegionImportFeed(
        regionImport = this,
        feedImportId = feedImportId,
        sequenceNumber = sequenceNumber,
      )
    feeds.add(feed)
    return feed
  }
}
