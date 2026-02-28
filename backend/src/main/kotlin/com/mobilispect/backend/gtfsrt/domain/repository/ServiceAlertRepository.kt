package com.mobilispect.backend.gtfsrt.domain.repository

import com.mobilispect.backend.gtfsrt.domain.model.ServiceAlert

/** Repository for persisting realtime service alerts. */
interface ServiceAlertRepository {
  fun saveAll(alerts: List<ServiceAlert>)
}
