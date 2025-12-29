package com.mobilispect.backend.infastructure.transit_land.cache

import java.util.UUID
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface TransitLandOnestopIdMappingRepository :
  JpaRepository<TransitLandOnestopIdMappingEntity, UUID> {
  fun findByFeedOnestopIdAndEntityTypeAndGtfsId(
    feedOnestopId: String,
    entityType: TransitLandEntityType,
    gtfsId: String,
  ): TransitLandOnestopIdMappingEntity?

  fun findAllByFeedOnestopIdAndEntityType(
    feedOnestopId: String,
    entityType: TransitLandEntityType,
  ): List<TransitLandOnestopIdMappingEntity>
}
