package com.mobilispect.backend.gtfsrt.infrastructure

import com.mobilispect.backend.gtfsrt.domain.model.TripUpdate
import com.mobilispect.backend.gtfsrt.domain.repository.TripUpdateRepository

/** In-memory trip update repository for testing. */
class InMemoryTripUpdateRepository : TripUpdateRepository {
  private val updates = mutableListOf<TripUpdate>()

  override fun saveAll(updates: List<TripUpdate>) {
    this.updates.addAll(updates)
  }

  fun findAll(): List<TripUpdate> = updates.toList()
}
