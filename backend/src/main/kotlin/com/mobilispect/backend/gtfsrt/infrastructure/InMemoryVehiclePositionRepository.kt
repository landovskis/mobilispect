package com.mobilispect.backend.gtfsrt.infrastructure

import com.mobilispect.backend.gtfsrt.domain.model.VehiclePosition
import com.mobilispect.backend.gtfsrt.domain.repository.VehiclePositionRepository

/** In-memory vehicle position repository for testing. */
class InMemoryVehiclePositionRepository : VehiclePositionRepository {
  private val positions = mutableListOf<VehiclePosition>()

  override fun saveAll(positions: List<VehiclePosition>) {
    this.positions.addAll(positions)
  }

  fun findAll(): List<VehiclePosition> = positions.toList()
}
