package com.mobilispect.backend.region.domain

import jakarta.persistence.Column
import jakarta.persistence.Embeddable
import jakarta.persistence.EmbeddedId
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import java.io.Serializable
import java.util.UUID

/** Composite primary key for the RegionImportFeed junction table. */
@Embeddable
data class RegionImportFeedId(
  @Column(name = "region_import_id") var regionImportId: UUID = UUID.randomUUID(),
  @Column(name = "feed_import_id") var feedImportId: UUID = UUID.randomUUID(),
) : Serializable

/**
 * Junction entity linking region imports to child feed imports. Tracks the relationship between the
 * parent region import job and each child feed import job.
 *
 * Per constitutional modular monolith requirements, this entity:
 * - References feed imports by UUID only (not by entity)
 * - Does not create a direct ORM relationship to the feed module
 */
@Entity
@Table(name = "region_import_feeds")
class RegionImportFeed(
  @EmbeddedId var id: RegionImportFeedId = RegionImportFeedId(),
  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(
    name = "region_import_id",
    referencedColumnName = "id",
    insertable = false,
    updatable = false,
  )
  var regionImport: RegionImport? = null,
  @Column(name = "child_job_execution_id") var childJobExecutionId: Long? = null,
  @Column(name = "sequence_number", nullable = false) var sequenceNumber: Int = 0,
) {
  /** Convenience property to get/set the feed import ID without accessing the composite key. */
  var feedImportId: UUID
    get() = id.feedImportId
    set(value) {
      id = id.copy(feedImportId = value)
    }

  constructor() :
    this(
      id = RegionImportFeedId(),
      regionImport = null,
      childJobExecutionId = null,
      sequenceNumber = 0,
    )

  constructor(
    regionImport: RegionImport,
    feedImportId: UUID,
    sequenceNumber: Int,
    childJobExecutionId: Long? = null,
  ) : this(
    id = RegionImportFeedId(regionImportId = regionImport.id.value, feedImportId = feedImportId),
    regionImport = regionImport,
    childJobExecutionId = childJobExecutionId,
    sequenceNumber = sequenceNumber,
  )

  /** Sets the child job execution ID when the feed import job is launched. */
  fun setChildJobExecution(jobExecutionId: Long) {
    childJobExecutionId = jobExecutionId
  }
}
