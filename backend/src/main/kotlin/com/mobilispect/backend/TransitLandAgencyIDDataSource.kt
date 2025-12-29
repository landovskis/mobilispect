package com.mobilispect.backend

import com.mobilispect.backend.infastructure.transit_land.cache.TransitLandEntityType
import com.mobilispect.backend.infastructure.transit_land.cache.TransitLandOnestopIdMappingEntity
import com.mobilispect.backend.infastructure.transit_land.cache.TransitLandOnestopIdMappingRepository
import com.mobilispect.backend.schedule.transit_land.TransitLandAPI
import com.mobilispect.backend.schedule.transit_land.api.TransitLandCredentialsRepository
import org.slf4j.LoggerFactory
import org.springframework.dao.DataIntegrityViolationException

/** A [AgencyIDDataSource] that uses transit.land for agency IDs. */
class TransitLandAgencyIDDataSource(
  private val transitLandAPI: TransitLandAPI,
  private val transitLandCredentialsRepository: TransitLandCredentialsRepository,
  private val mappingRepository: TransitLandOnestopIdMappingRepository,
) : AgencyIDDataSource {
  private val logger = LoggerFactory.getLogger(TransitLandAgencyIDDataSource::class.java)

  override fun agencyIDs(feedID: String): Result<Map<String, String>> {
    val cachedMappings =
      mappingRepository.findAllByFeedOnestopIdAndEntityType(feedID, TransitLandEntityType.AGENCY)
    if (cachedMappings.isNotEmpty()) {
      return Result.success(cachedMappings.associate { it.gtfsId to it.onestopId })
    }

    val apiKey =
      transitLandCredentialsRepository.get() ?: return Result.failure(Exception("Missing API key"))
    return transitLandAPI.agencies(apiKey = apiKey, feedID = feedID).map { agencies ->
      val mappings =
        agencies.agencies
          .filter { it.agencyID != null }
          .map { item ->
            TransitLandOnestopIdMappingEntity(
              feedOnestopId = feedID,
              entityType = TransitLandEntityType.AGENCY,
              gtfsId = item.agencyID!!,
              onestopId = item.id,
            )
          }
      if (mappings.isNotEmpty()) {
        try {
          mappingRepository.saveAll(mappings)
        } catch (e: DataIntegrityViolationException) {
          logger.warn("Agency onestop ID mapping already exists for feed {}", feedID)
        }
      }
      mappings.associate { it.gtfsId to it.onestopId }
    }
  }
}
