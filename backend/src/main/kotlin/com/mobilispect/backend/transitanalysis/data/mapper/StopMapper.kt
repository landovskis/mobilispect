package com.mobilispect.backend.transitanalysis.data.mapper

import com.mobilispect.backend.feed.data.entity.FeedEntity
import com.mobilispect.backend.feed.model.ids.FeedId
import com.mobilispect.backend.transitanalysis.data.entity.StopEntity
import com.mobilispect.backend.transitanalysis.domain.model.Stop
import com.mobilispect.backend.transitanalysis.domain.model.ids.StopId
import org.springframework.stereotype.Component

/**
 * Mapper for bidirectional conversion between Stop domain model and StopEntity data model.
 *
 * Domain models use @JvmInline value class IDs for type safety.
 * Data entities use plain String IDs for Hibernate 7 compatibility.
 */
@Component
class StopMapper {

    /**
     * Converts data entity to domain model.
     * Extracts feed ID from the feed relationship.
     */
    fun toDomain(entity: StopEntity): Stop =
        Stop(
            stopOnestopId = StopId(entity.stopOnestopId),
            feedId = FeedId(entity.feed.feedOnestopId),
            gtfsStopId = entity.gtfsStopId,
            name = entity.name,
            latitude = entity.latitude,
            longitude = entity.longitude,
            stopCode = entity.stopCode,
            stopDesc = entity.stopDesc,
            zoneId = entity.zoneId,
            stopUrl = entity.stopUrl,
            locationType = entity.locationType,
            parentStation = entity.parentStation,
            active = entity.active,
            firstSeen = entity.firstSeen,
            lastSeen = entity.lastSeen,
            createdAt = entity.createdAt,
            updatedAt = entity.updatedAt
        )

    /**
     * Converts domain model to data entity.
     * Requires the feed entity to be provided by the caller for the relationship.
     *
     * @param domain The domain model to convert
     * @param feedEntity The feed entity this stop belongs to
     */
    fun toEntity(domain: Stop, feedEntity: FeedEntity): StopEntity =
        StopEntity(
            stopOnestopId = domain.stopOnestopId.value,
            feed = feedEntity,
            gtfsStopId = domain.gtfsStopId,
            name = domain.name,
            latitude = domain.latitude,
            longitude = domain.longitude,
            stopCode = domain.stopCode,
            stopDesc = domain.stopDesc,
            zoneId = domain.zoneId,
            stopUrl = domain.stopUrl,
            locationType = domain.locationType,
            parentStation = domain.parentStation,
            active = domain.active,
            firstSeen = domain.firstSeen,
            lastSeen = domain.lastSeen,
            createdAt = domain.createdAt,
            updatedAt = domain.updatedAt
        )
}
