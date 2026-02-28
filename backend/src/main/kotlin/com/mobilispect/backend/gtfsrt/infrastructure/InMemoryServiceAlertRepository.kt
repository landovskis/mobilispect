package com.mobilispect.backend.gtfsrt.infrastructure

import com.mobilispect.backend.gtfsrt.domain.model.ServiceAlert
import com.mobilispect.backend.gtfsrt.domain.repository.ServiceAlertRepository

/** In-memory service alert repository for testing. */
class InMemoryServiceAlertRepository : ServiceAlertRepository {
  private val alerts = mutableListOf<ServiceAlert>()

  override fun saveAll(alerts: List<ServiceAlert>) {
    this.alerts.addAll(alerts)
  }

  fun findAll(): List<ServiceAlert> = alerts.toList()
}
