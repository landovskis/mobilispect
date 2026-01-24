package com.mobilispect.backend.route.application

import com.mobilispect.backend.route.RouteId
import com.mobilispect.backend.route.api.dto.FrequencyDTO
import com.mobilispect.backend.route.api.dto.RouteDTO
import com.mobilispect.backend.route.api.dto.RouteVariantDTO
import com.mobilispect.backend.route.domain.model.ids.VariantHash
import com.mobilispect.backend.route.domain.repository.FrequencyRepository
import com.mobilispect.backend.route.domain.repository.RouteRepository
import com.mobilispect.backend.route.domain.repository.RouteVariantRepository
import com.mobilispect.backend.route.domain.repository.VariantDepartureRepository
import com.mobilispect.backend.route.domain.repository.VariantScheduleRepository
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import org.springframework.stereotype.Service

/** Query service for frequency-related operations. */
@Service
class FrequencyQueryService(
  private val routeRepository: RouteRepository,
  private val routeVariantRepository: RouteVariantRepository,
  private val frequencyRepository: FrequencyRepository,
  private val variantScheduleRepository: VariantScheduleRepository,
  private val variantDepartureRepository: VariantDepartureRepository,
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
    routeVariantRepository.findByRouteId(routeId).map { variant ->
      val schedule = variantScheduleRepository.findByVariantId(variant.id.value)

      RouteVariantDTO(
        id = variant.id.value,
        routeId = variant.routeId.value,
        directionId = variant.directionId,
        headsign = variant.headsign,
        stopCount = variant.stopCount,
        stopPattern = variant.stopPattern,
        stopNames = extractStopNames(variant.stopNamePattern, variant.stopPattern),
        firstStopId = variant.firstStopId,
        lastStopId = variant.lastStopId,
        firstDepartureTime = schedule?.firstDepartureTime,
        lastDepartureTime = schedule?.lastDepartureTime,
        scheduleTripCount = schedule?.tripCount,
      )
    }

  private fun extractStopNames(stopNamePattern: String?, stopPattern: String): List<String> {
    val pattern = stopNamePattern?.takeIf { it.isNotBlank() } ?: stopPattern
    return pattern.split("|").filter { it.isNotBlank() }
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

  /**
   * Get complete schedule (all departure times) for a variant.
   *
   * @param variantHash The variant hash
   * @return List of departure times formatted as HH:mm
   */
  fun getCompleteSchedule(variantHash: VariantHash): List<String> {
    val departures =
      variantDepartureRepository.findByVariantIdOrderByDepartureTime(variantHash.value)
    val formatter = DateTimeFormatter.ofPattern("HH:mm")
    return departures.map { it.departureTime.format(formatter) }
  }
}
