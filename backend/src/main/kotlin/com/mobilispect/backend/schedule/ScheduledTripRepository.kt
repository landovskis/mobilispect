package com.mobilispect.backend.schedule

import org.springframework.data.repository.CrudRepository
import org.springframework.data.rest.core.annotation.RestResource

interface ScheduledTripRepository : CrudRepository<ScheduledTrip, String> {
  override fun findAll(): List<ScheduledTrip>

  @RestResource(exported = false) fun save(entity: ScheduledTrip): ScheduledTrip

  @RestResource(exported = false) override fun deleteAll()
}
