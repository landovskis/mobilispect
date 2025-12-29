package com.mobilispect.backend.infastructure.transit_land

import com.mobilispect.backend.infastructure.StopIDDataSource
import com.mobilispect.backend.infastructure.transit_land.cache.TransitLandEntityType
import com.mobilispect.backend.infastructure.transit_land.cache.TransitLandOnestopIdMappingEntity
import com.mobilispect.backend.infastructure.transit_land.cache.TransitLandOnestopIdMappingRepository
import com.mobilispect.backend.schedule.transit_land.TransitLandAPI
import com.mobilispect.backend.schedule.transit_land.api.TransitLandCredentialsRepository
import org.slf4j.LoggerFactory
import org.springframework.dao.DataIntegrityViolationException

/** A [StopIDDataSource] that uses transit.land for stop IDs. */
class TransitLandStopIDDataSource(
  private val transitLandAPI: TransitLandAPI,
  private val transitLandCredentialsRepository: TransitLandCredentialsRepository,
  private val mappingRepository: TransitLandOnestopIdMappingRepository,
) : StopIDDataSource {
  private val logger = LoggerFactory.getLogger(TransitLandStopIDDataSource::class.java)

  override fun stop(feedID: String, stopID: String): Result<String> {
    val cachedMapping =
      mappingRepository.findByFeedOnestopIdAndEntityTypeAndGtfsId(
        feedID,
        TransitLandEntityType.STOP,
        stopID,
      )
    if (cachedMapping != null) {
      return Result.success(cachedMapping.onestopId)
    }

    val apiKey =
      transitLandCredentialsRepository.get() ?: return Result.failure(Exception("Missing API key"))
    return transitLandAPI.stop(apiKey = apiKey, feedID = feedID, stopID = stopID).map { result ->
      val mapping =
        TransitLandOnestopIdMappingEntity(
          feedOnestopId = feedID,
          entityType = TransitLandEntityType.STOP,
          gtfsId = stopID,
          onestopId = result.uid,
        )
      try {
        mappingRepository.save(mapping)
      } catch (e: DataIntegrityViolationException) {
        logger.warn("Stop onestop ID mapping already exists for feed {}", feedID)
      }
      result.uid
    }
  }
}
