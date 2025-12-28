package com.mobilispect.backend.stop.internal

import com.mobilispect.backend.feed.domain.model.ids.FeedId
import com.mobilispect.backend.stop.api.StopDTO
import com.mobilispect.backend.stop.api.StopQueryApi
import com.mobilispect.backend.stop.domain.model.ids.StopId
import com.mobilispect.backend.stop.domain.repository.StopRepository
import org.springframework.stereotype.Component

/**
 * Internal implementation of StopQueryApi.
 *
 * Bridges the Stop module API to the underlying repository layer.
 * This implementation is internal to the stop module and should not be accessed directly by other modules.
 */
@Component
internal class StopQueryApiImpl(
    private val stopRepository: StopRepository
) : StopQueryApi {

    override fun findStopById(stopId: StopId): StopDTO? =
        stopRepository.findById(stopId)?.let { stop ->
            StopDTO(
                stopId = stop.stopOnestopId,
                feedId = stop.feedId,
                gtfsStopId = stop.gtfsStopId,
                name = stop.name,
                latitude = stop.latitude,
                longitude = stop.longitude,
                locationType = stop.locationType,
                parentStationId = stop.parentStation,
                wheelchairBoarding = null, // Not in current Stop model
                platformCode = null, // Not in current Stop model
                zoneId = stop.zoneId,
                createdAt = stop.createdAt,
                updatedAt = stop.updatedAt
            )
        }

    override fun findStopsByFeed(feedId: FeedId): List<StopDTO> =
        stopRepository.findAll()
            .filter { it.feedId == feedId }
            .map { stop ->
                StopDTO(
                    stopId = stop.stopOnestopId,
                    feedId = stop.feedId,
                    gtfsStopId = stop.gtfsStopId,
                    name = stop.name,
                    latitude = stop.latitude,
                    longitude = stop.longitude,
                    locationType = stop.locationType,
                    parentStationId = stop.parentStation,
                    wheelchairBoarding = null,
                    platformCode = null,
                    zoneId = stop.zoneId,
                    createdAt = stop.createdAt,
                    updatedAt = stop.updatedAt
                )
            }

    override fun validateStopExists(stopId: StopId): Boolean =
        stopRepository.findById(stopId) != null
}
