package com.mobilispect.backend.route.application

import com.mobilispect.backend.route.api.dto.RouteDTO
import com.mobilispect.backend.route.api.dto.RouteHourlyStatsDTO
import com.mobilispect.backend.route.api.dto.RouteVariantDTO
import com.mobilispect.backend.route.domain.model.ids.RouteId
import com.mobilispect.backend.route.domain.repository.RouteHourlyStatRepository
import com.mobilispect.backend.route.domain.repository.RouteRepository
import com.mobilispect.backend.route.domain.repository.RouteVariantRepository
import com.mobilispect.backend.route.domain.repository.StopSpacingRepository
import org.springframework.stereotype.Service

/** Query service for frequency-related operations. */
@Service
class FrequencyQueryService(
  private val routeRepository: RouteRepository,
  private val routeVariantRepository: RouteVariantRepository,
  private val stopSpacingRepository: StopSpacingRepository,
  private val routeHourlyStatRepository: RouteHourlyStatRepository,
) {
  fun getRoute(routeId: RouteId): RouteDTO? =
    routeRepository.findById(routeId)?.let {
      val variants = getVariantsByRoute(routeId)
      val hourlyStats = getHourlyStatsByRoute(routeId)
      RouteDTO(
        id = it.id.value,
        agencyId = it.agencyId.value,
        shortName = it.shortName,
        longName = it.longName,
        routeType = it.routeType,
        active = it.active,
        variants = variants,
        hourlyStats = hourlyStats,
      )
    }

  fun getVariantsByRoute(routeId: RouteId): List<RouteVariantDTO> =
    routeVariantRepository.findByRouteId(routeId).map {
      val spacings = stopSpacingRepository.findByVariantOrderBySequence(it.id.value)
      val spacingMeters = spacings.map { spacing -> spacing.distanceMeters }
      val averageSpacingMeters = spacingMeters.takeIf { list -> list.isNotEmpty() }?.average()
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
        averageStopSpacingMeters = averageSpacingMeters,
        stopSpacingMeters = spacingMeters,
        stopSpacingClassification = classifyStopSpacingMeters(averageSpacingMeters),
      )
    }

  private fun extractStopNames(stopNamePattern: String?, stops: List<String>): List<String> {
    val pattern = stopNamePattern?.takeIf { it.isNotBlank() }
    return pattern?.split("|")?.filter { it.isNotBlank() } ?: stops
  }

  private fun getHourlyStatsByRoute(routeId: RouteId): List<RouteHourlyStatsDTO> {
    val latestServiceDate = routeHourlyStatRepository.findLatestServiceDate(routeId.value)
    if (latestServiceDate == null) {
      return emptyList()
    }
    return routeHourlyStatRepository
      .findByRouteIdAndServiceDateOrderByDayTypeAscDirectionIdAscHourOfDayAsc(
        routeId.value,
        latestServiceDate,
      )
      .map {
        RouteHourlyStatsDTO(
          serviceDate = it.serviceDate.toString(),
          directionId = it.directionId?.toInt(),
          dayType = it.dayType.name,
          hourOfDay = it.hourOfDay,
          tripCount = it.tripCount,
          averageSpeedKph = it.averageSpeedKph,
        )
      }
  }

  private fun classifyStopSpacingMeters(averageStopSpacingMeters: Double?): String? {
    if (averageStopSpacingMeters == null) {
      return null
    }
    return when (averageStopSpacingMeters) {
      in 300.0..700.0 -> "local"
      in 700.0..1500.0 -> "rapid"
      in 1500.0..3000.0 -> "region-local"
      in 3000.0..10000.0 -> "region-rapid"
      in 10000.0..15000.0 -> "region-express"
      else -> null
    }
  }
}
