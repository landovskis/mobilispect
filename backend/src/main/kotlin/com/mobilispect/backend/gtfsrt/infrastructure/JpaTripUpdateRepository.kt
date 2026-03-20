package com.mobilispect.backend.gtfsrt.infrastructure

import com.mobilispect.backend.gtfsrt.data.entity.TripUpdateEntity
import com.mobilispect.backend.gtfsrt.data.repository.TripUpdateJpaRepository
import com.mobilispect.backend.gtfsrt.domain.model.TripUpdate
import com.mobilispect.backend.gtfsrt.domain.repository.TripUpdateRepository
import org.springframework.stereotype.Repository

@Repository
class JpaTripUpdateRepository(private val jpaRepository: TripUpdateJpaRepository) :
  TripUpdateRepository {

  override fun saveAll(updates: List<TripUpdate>) {
    jpaRepository.saveAll(updates.map { it.toEntity() })
  }

  private fun TripUpdate.toEntity() =
    TripUpdateEntity(
      feedId = feedId.value,
      tripId = tripId,
      routeId = routeId,
      vehicleId = vehicleId,
      timestamp = timestamp,
      delay = delay,
      scheduleRelationship = scheduleRelationship?.name,
    )
}
