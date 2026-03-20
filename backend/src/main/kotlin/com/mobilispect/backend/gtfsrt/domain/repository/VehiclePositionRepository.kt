package com.mobilispect.backend.gtfsrt.domain.repository

import com.mobilispect.backend.gtfsrt.domain.model.VehiclePosition

/** Repository for persisting realtime vehicle positions. */
interface VehiclePositionRepository {
  fun saveAll(positions: List<VehiclePosition>)
}
