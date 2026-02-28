package com.mobilispect.backend.gtfsrt.domain.repository

import com.mobilispect.backend.gtfsrt.domain.model.TripUpdate

/** Repository for persisting realtime trip updates. */
interface TripUpdateRepository {
  fun saveAll(updates: List<TripUpdate>)
}
