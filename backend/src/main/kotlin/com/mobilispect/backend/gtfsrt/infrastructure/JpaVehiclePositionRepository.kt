package com.mobilispect.backend.gtfsrt.infrastructure

import com.mobilispect.backend.gtfsrt.data.entity.VehiclePositionEntity
import com.mobilispect.backend.gtfsrt.data.repository.VehiclePositionJpaRepository
import com.mobilispect.backend.gtfsrt.domain.model.VehiclePosition
import com.mobilispect.backend.gtfsrt.domain.repository.VehiclePositionRepository
import org.springframework.stereotype.Repository

@Repository
class JpaVehiclePositionRepository(private val jpaRepository: VehiclePositionJpaRepository) :
  VehiclePositionRepository {

  override fun saveAll(positions: List<VehiclePosition>) {
    jpaRepository.saveAll(positions.map { it.toEntity() })
  }

  private fun VehiclePosition.toEntity() =
    VehiclePositionEntity(
      feedId = feedId.value,
      vehicleId = vehicleId,
      tripId = tripId,
      routeId = routeId,
      latitude = latitude,
      longitude = longitude,
      bearing = bearing,
      speed = speed,
      currentStopSequence = currentStopSequence,
      currentStatus = currentStatus?.name,
      timestamp = timestamp,
      congestionLevel = congestionLevel?.name,
      occupancyStatus = occupancyStatus?.name,
    )
}
