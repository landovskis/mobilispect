package com.mobilispect.backend.stop.data.mapper

import com.mobilispect.backend.feed.model.ids.FeedId
import com.mobilispect.backend.stop.data.entity.StopEntity
import com.mobilispect.backend.stop.domain.model.Stop
import com.mobilispect.backend.stop.domain.model.ids.StopId
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
     * Extracts feed ID from the feedOnestopId column.
     */
    fun toDomain(entity: StopEntity): Stop =
        Stop(
            stopOnestopId = StopId(entity.stopOnestopId),
            feedId = FeedId(entity.feedOnestopId),
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
     * Maps feed ID directly to feedOnestopId column without entity navigation.
     *
     * @param domain The domain model to convert
     */
    fun toEntity(domain: Stop): StopEntity =
        StopEntity(
            stopOnestopId = domain.stopOnestopId.value,
            feedOnestopId = domain.feedId.value,
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
