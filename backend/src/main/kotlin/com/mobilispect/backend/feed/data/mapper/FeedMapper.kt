package com.mobilispect.backend.feed.data.mapper

import com.mobilispect.backend.feed.data.entity.FeedEntity
import com.mobilispect.backend.feed.domain.model.Feed
import com.mobilispect.backend.feed.domain.model.ids.FeedId
import com.mobilispect.backend.feed.model.ids.RegionId
import org.springframework.stereotype.Component

/**
 * Mapper for bidirectional conversion between Feed domain model and FeedEntity data model.
 *
 * Domain models use @JvmInline value class IDs for type safety.
 * Data entities use plain String IDs for Hibernate 7 compatibility.
 *
 * Per ADR 0009 FK-only pattern, regions are managed as Set<RegionId> in domain
 * and MutableSet<MetropolitanRegionEntity> in entity.
 */
@Component
class FeedMapper {

    /**
     * Converts data entity to domain model.
     * Extracts region IDs from the many-to-many regions collection.
     */
    fun toDomain(entity: FeedEntity): Feed =
        Feed(
            feedId = FeedId(entity.feedOnestopId),
            name = entity.name,
            operatorName = entity.operatorName,
            specType = entity.specType,
            downloadUrl = entity.downloadUrl,
            staticFeedUrl = entity.staticFeedUrl,
            realtimeFeedUrl = entity.realtimeFeedUrl,
            currentVersionSha1 = entity.currentVersionSha1,
            status = entity.status,
            regionIds = entity.regions.map { RegionId(it.regionOnestopId) }.toSet(),
            lastCheckedAt = entity.lastCheckedAt,
            lastUpdatedAt = entity.lastUpdatedAt,
            lastDiscoveredAt = entity.lastDiscoveredAt,
            createdAt = entity.createdAt,
            updatedAt = entity.updatedAt
        )

    /**
     * Converts domain model to data entity.
     *
     * Note: regionIds are NOT mapped here - they must be managed separately
     * through the feed_regions junction table to maintain FK-only pattern.
     * The repository implementation is responsible for populating the regions collection.
     *
     * @param domain The domain model to convert
     */
    fun toEntity(domain: Feed): FeedEntity =
        FeedEntity(
            feedOnestopId = domain.feedId.value,
            name = domain.name,
            specType = domain.specType,
            downloadUrl = domain.downloadUrl,
            staticFeedUrl = domain.staticFeedUrl,
            realtimeFeedUrl = domain.realtimeFeedUrl,
            operatorName = domain.operatorName,
            currentVersionSha1 = domain.currentVersionSha1,
            status = domain.status,
            lastCheckedAt = domain.lastCheckedAt,
            lastUpdatedAt = domain.lastUpdatedAt,
            lastDiscoveredAt = domain.lastDiscoveredAt,
            createdAt = domain.createdAt,
            updatedAt = domain.updatedAt
        )
        // Note: regions collection populated separately by repository implementation
}
