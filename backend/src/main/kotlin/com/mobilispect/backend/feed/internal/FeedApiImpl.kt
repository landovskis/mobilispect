package com.mobilispect.backend.feed.internal

import com.mobilispect.backend.feed.FeedApi
import com.mobilispect.backend.feed.api.FeedDTO
import com.mobilispect.backend.feed.domain.FeedImport
import com.mobilispect.backend.feed.domain.model.Feed
import com.mobilispect.backend.feed.domain.model.ids.FeedId
import com.mobilispect.backend.feed.domain.repository.FeedRepository
import com.mobilispect.backend.feed.model.ImportStatus
import com.mobilispect.backend.feed.model.ImportTriggerType
import com.mobilispect.backend.feed.model.ids.ImportId
import com.mobilispect.backend.feed.repository.FeedImportRepository
import com.mobilispect.backend.feed.service.FeedImportService
import com.mobilispect.backend.feed.service.FeedImportSyncService
import com.mobilispect.backend.region.RegionId
import java.util.UUID
import org.springframework.stereotype.Service

/**
 * Implementation of the Feed module's public query API.
 *
 * This service provides cross-module access to feed data while maintaining proper module
 * boundaries. It converts domain models to public DTOs.
 */
@Service
internal class FeedApiImpl(
  private val feedRepository: FeedRepository,
  private val feedImportService: FeedImportService,
  private val feedImportSyncService: FeedImportSyncService,
  private val feedImportRepository: FeedImportRepository,
) : FeedApi {

  override fun findFeedById(feedId: FeedId): FeedDTO? {
    return feedRepository.findById(feedId)?.toDTO()
  }

  override fun findFeedsByRegion(regionId: RegionId): List<Feed> {
    return feedRepository.findByRegionId(regionId)
  }

  override fun findActiveFeedsByRegion(regionId: RegionId): List<Feed> {
    return feedRepository.findByRegionIdAndStatusIn(
      regionId,
      setOf(com.mobilispect.backend.feed.model.FeedStatus.ACTIVE),
    )
  }

  override fun getFeedVersion(feedId: FeedId): String? {
    return feedRepository.findById(feedId)?.currentVersionSha1
  }

  override fun import(feedId: FeedId, triggerType: ImportTriggerType): FeedImport =
    feedImportService.import(feedId, triggerType)

  override fun importSync(feedId: FeedId, triggerType: ImportTriggerType): FeedImport =
    feedImportSyncService.importSync(feedId, triggerType)

  override fun getImportStatus(importId: UUID): ImportStatus? {
    return feedImportRepository.findByImportId(ImportId(importId)).map { it.status }.orElse(null)
  }

  /** Converts the domain model to public DTO. */
  private fun Feed.toDTO(): FeedDTO {
    return FeedDTO(
      feedId = this.feedId,
      name = this.name,
      specType = this.specType,
      downloadUrl = this.downloadUrl,
      currentVersionSha1 = this.currentVersionSha1,
      status = this.status,
      regionIds = this.regionIds,
      lastCheckedAt = this.lastCheckedAt,
      lastUpdatedAt = this.lastUpdatedAt,
      lastDiscoveredAt = this.lastDiscoveredAt,
      createdAt = this.createdAt,
      updatedAt = this.updatedAt,
    )
  }
}
