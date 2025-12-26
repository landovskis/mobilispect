package com.mobilispect.backend.feed.internal

import com.mobilispect.backend.feed.api.FeedDTO
import com.mobilispect.backend.feed.api.FeedQueryApi
import com.mobilispect.backend.feed.domain.model.Feed
import com.mobilispect.backend.feed.domain.model.ids.FeedId
import com.mobilispect.backend.feed.domain.repository.FeedRepository
import com.mobilispect.backend.feed.model.ids.RegionId
import org.springframework.stereotype.Service

/**
 * Implementation of the Feed module's public query API.
 *
 * This service provides cross-module access to feed data while maintaining
 * proper module boundaries. It converts domain models to public DTOs.
 */
@Service
internal class FeedQueryApiImpl(
    private val feedRepository: FeedRepository
) : FeedQueryApi {

    override fun findFeedById(feedId: FeedId): FeedDTO? {
        return feedRepository.findById(feedId)
            ?.toDTO()
    }

    override fun findFeedsByRegion(regionId: RegionId): List<FeedDTO> {
        return feedRepository.findByRegionId(regionId)
            .map { it.toDTO() }
    }

    override fun getFeedVersion(feedId: FeedId): String? {
        return feedRepository.findById(feedId)
            ?.currentVersionSha1
    }

    /**
     * Converts domain model to public DTO.
     */
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
            updatedAt = this.updatedAt
        )
    }
}
