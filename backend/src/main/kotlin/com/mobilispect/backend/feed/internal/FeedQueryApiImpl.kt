package com.mobilispect.backend.feed.internal

import com.mobilispect.backend.feed.api.FeedDTO
import com.mobilispect.backend.feed.api.FeedQueryApi
import com.mobilispect.backend.feed.model.FeedEntity
import com.mobilispect.backend.feed.model.ids.FeedId
import com.mobilispect.backend.feed.model.ids.RegionId
import com.mobilispect.backend.feed.repository.FeedRepository
import org.springframework.stereotype.Service

/**
 * Implementation of the Feed module's public query API.
 *
 * This service provides cross-module access to feed data while maintaining
 * proper module boundaries. It converts internal entities to public DTOs.
 */
@Service
internal class FeedQueryApiImpl(
    private val feedRepository: FeedRepository
) : FeedQueryApi {

    override fun findFeedById(feedId: FeedId): FeedDTO? {
        return feedRepository.findByFeedOnestopId(feedId.value)
            .map { it.toDTO() }
            .orElse(null)
    }

    override fun findFeedsByRegion(regionId: RegionId): List<FeedDTO> {
        return feedRepository.findAllByRegionRegionOnestopId(regionId)
            .map { it.toDTO() }
    }

    override fun getFeedVersion(feedId: FeedId): String? {
        return feedRepository.findByFeedOnestopId(feedId.value)
            .map { it.currentVersionSha1 }
            .orElse(null)
    }

    /**
     * Converts internal entity to public DTO.
     */
    private fun FeedEntity.toDTO(): FeedDTO {
        return FeedDTO(
            feedId = FeedId(this.feedOnestopId),
            name = this.name,
            specType = this.specType,
            downloadUrl = this.downloadUrl,
            currentVersionSha1 = this.currentVersionSha1,
            status = this.status,
            regionIds = this.regions.map { RegionId(it.regionOnestopId.value) }.toSet(),
            lastCheckedAt = this.lastCheckedAt,
            lastUpdatedAt = this.lastUpdatedAt,
            lastDiscoveredAt = this.lastDiscoveredAt,
            createdAt = this.createdAt,
            updatedAt = this.updatedAt
        )
    }
}
