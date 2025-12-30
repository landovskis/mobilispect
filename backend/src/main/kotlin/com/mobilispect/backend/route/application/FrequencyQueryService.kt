package com.mobilispect.backend.route.application

import com.mobilispect.backend.route.api.dto.FrequencyDTO
import com.mobilispect.backend.route.api.dto.RouteDTO
import com.mobilispect.backend.route.api.dto.RouteVariantDTO
import com.mobilispect.backend.route.domain.model.ids.RouteId
import com.mobilispect.backend.route.domain.model.ids.VariantHash
import com.mobilispect.backend.route.domain.repository.FrequencyRepository
import com.mobilispect.backend.route.domain.repository.RouteRepository
import com.mobilispect.backend.route.domain.repository.RouteVariantRepository
import java.time.LocalDate
import org.springframework.stereotype.Service

/** Query service for frequency-related operations. */
@Service
class FrequencyQueryService(
  private val routeRepository: RouteRepository,
  private val routeVariantRepository: RouteVariantRepository,
  private val frequencyRepository: FrequencyRepository,
) {
  fun getRoute(routeId: RouteId): RouteDTO? =
    routeRepository.findById(routeId)?.let {
      RouteDTO(
        id = it.id.value,
        agencyId = it.agencyId.value,
        shortName = it.shortName,
        longName = it.longName,
        routeType = it.routeType,
        active = it.active,
      )
    }

  fun getVariantsByRoute(routeId: RouteId): List<RouteVariantDTO> =
    routeVariantRepository.findByRouteId(routeId).map {
      RouteVariantDTO(
        id = it.id.value,
        routeId = it.routeId.value,
        directionId = it.directionId,
        headsign = it.headsign,
        stopCount = it.stopCount,
        stopPattern = it.stopPattern,
        stopNames = extractStopNames(it.stopNamePattern, it.stops),
        firstStopId = it.firstStopId,
        lastStopId = it.lastStopId,
      )
    }

  private fun extractStopNames(stopNamePattern: String?, stops: List<String>): List<String> {
    val pattern = stopNamePattern?.takeIf { it.isNotBlank() }
    return pattern?.split("|")?.filter { it.isNotBlank() } ?: stops
  }

  fun getFrequenciesForVariant(
    variantHash: VariantHash,
    serviceDate: LocalDate?,
  ): List<FrequencyDTO> {
    val variantId = variantHash.value
    val freqs =
      if (serviceDate != null) {
        frequencyRepository
          .findByVariantAndServiceDate(
            variantId,
            serviceDate,
            org.springframework.data.domain.Pageable.unpaged(),
          )
          .content
      } else {
        frequencyRepository
          .findByVariant(variantId, org.springframework.data.domain.Pageable.unpaged())
          .content
      }
    return freqs.map {
      FrequencyDTO(
        id = it.id.toString(),
        variantId = it.variantId,
        serviceDate = it.serviceDate.toString(),
        timePeriod = it.timePeriod,
        averageHeadwayMinutes = it.averageHeadway,
        minHeadwayMinutes = it.minHeadway,
        maxHeadwayMinutes = it.maxHeadway,
        tripCount = it.tripCount,
        isIrregular = it.isIrregular,
      )
    }
  }

  private fun classifyStopSpacing(averageStopSpacingKm: Double?): String? {
    return when {
      averageStopSpacingKm == null -> null
      averageStopSpacingKm < 0.5 -> "local"
      averageStopSpacingKm <= 1.0 -> "rapid"
      else -> "express"
    }
  }
}
