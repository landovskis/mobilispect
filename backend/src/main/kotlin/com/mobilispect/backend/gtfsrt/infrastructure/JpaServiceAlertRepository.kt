package com.mobilispect.backend.gtfsrt.infrastructure

import com.mobilispect.backend.gtfsrt.data.entity.ServiceAlertEntity
import com.mobilispect.backend.gtfsrt.data.repository.ServiceAlertJpaRepository
import com.mobilispect.backend.gtfsrt.domain.model.ServiceAlert
import com.mobilispect.backend.gtfsrt.domain.repository.ServiceAlertRepository
import org.springframework.stereotype.Repository

@Repository
class JpaServiceAlertRepository(
  private val jpaRepository: ServiceAlertJpaRepository,
) : ServiceAlertRepository {

  override fun saveAll(alerts: List<ServiceAlert>) {
    jpaRepository.saveAll(alerts.map { it.toEntity() })
  }

  private fun ServiceAlert.toEntity() = ServiceAlertEntity(
    feedId = feedId.value,
    alertId = alertId,
    cause = cause?.name,
    effect = effect?.name,
    headerText = headerText,
    descriptionText = descriptionText,
    url = url,
    timestamp = timestamp,
  )
}
