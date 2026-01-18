package com.mobilispect.backend.route.batch.spacing

import com.mobilispect.backend.feed.api.GTFSData
import com.mobilispect.backend.feed.api.GTFSStop
import com.mobilispect.backend.route.domain.model.RouteVariant
import com.mobilispect.backend.route.domain.model.StopSpacing
import com.mobilispect.backend.route.domain.repository.RouteVariantRepository
import com.mobilispect.backend.route.domain.repository.StopSpacingRepository
import java.time.Instant
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import org.slf4j.LoggerFactory
import org.springframework.batch.core.step.StepExecution
import org.springframework.stereotype.Service

@Service
class StopSpacingImportService(
  private val routeVariantRepository: RouteVariantRepository,
  private val stopSpacingRepository: StopSpacingRepository,
) {
  private val logger = LoggerFactory.getLogger(StopSpacingImportService::class.java)

  fun execute(stepExecution: StepExecution) {
    val parsedData =
      stepExecution.jobExecution.executionContext.get("parsedData") as? GTFSData
        ?: throw IllegalStateException("ParsedGtfsData not found in job execution context")

    val stopsById = parsedData.stops.associateBy { it.stopId.value }
    val persistedVariants = routeVariantRepository.findAll()

    var variantsProcessed = 0
    var spacingsCreated = 0

    persistedVariants.forEach { variant ->
      val spacings = calculateStopSpacings(variant, stopsById)
      if (spacings.isEmpty()) {
        logger.debug(
          "Variant {} has no valid stop spacing data (insufficient coordinates)",
          variant.id.value,
        )
        return@forEach
      }

      if (stopSpacingRepository.existsByVariant(variant.id.value)) {
        stopSpacingRepository.deleteByVariant(variant.id.value)
      }

      val savedSpacings = stopSpacingRepository.saveAll(spacings)
      spacingsCreated += savedSpacings.count()
      variantsProcessed++

      val distances = spacings.map { it.distanceMeters }
      logger.info(
        "Created {} spacing records for variant {} (avg: {} m, min: {} m, max: {} m)",
        spacings.size,
        variant.id.value,
        "%.0f".format(distances.average()),
        "%.0f".format(distances.minOrNull() ?: 0.0),
        "%.0f".format(distances.maxOrNull() ?: 0.0),
      )
    }

    logger.info(
      "Persisted stop spacing records (variants={}, spacings={})",
      variantsProcessed,
      spacingsCreated,
    )

    val stepContext = stepExecution.executionContext
    stepContext.putInt("variantsProcessed", variantsProcessed)
    stepContext.putInt("spacingsCreated", spacingsCreated)

    val jobContext = stepExecution.jobExecution.executionContext
    jobContext.putInt("stopSpacingVariantsProcessed", variantsProcessed)
    jobContext.putInt("stopSpacingRecordsCreated", spacingsCreated)
  }

  private fun calculateStopSpacings(
    variant: RouteVariant,
    stopsById: Map<String, GTFSStop>,
  ): List<StopSpacing> {
    val stopIds = variant.stopPattern.split("|")
    if (stopIds.size < 2) {
      return emptyList()
    }

    val calculatedAt = Instant.now()
    val spacings = mutableListOf<StopSpacing>()

    for (i in 0 until stopIds.size - 1) {
      val fromStopId = stopIds[i]
      val toStopId = stopIds[i + 1]

      val fromStop = stopsById[fromStopId]
      val toStop = stopsById[toStopId]

      if (
        fromStop?.latitude != null &&
          fromStop.longitude != null &&
          toStop?.latitude != null &&
          toStop.longitude != null
      ) {
        val distanceMeters =
          haversineDistanceMeters(
            fromStop.latitude,
            fromStop.longitude,
            toStop.latitude,
            toStop.longitude,
          )

        spacings.add(
          StopSpacing(
            variantId = variant.id.value,
            fromStopId = fromStopId,
            toStopId = toStopId,
            stopSequence = i,
            distanceMeters = distanceMeters,
            calculatedAt = calculatedAt,
          )
        )
      }
    }

    return spacings
  }

  private fun haversineDistanceMeters(
    lat1: Double,
    lon1: Double,
    lat2: Double,
    lon2: Double,
  ): Double {
    val earthRadiusKm = 6371.0
    val dLat = Math.toRadians(lat2 - lat1)
    val dLon = Math.toRadians(lon2 - lon1)
    val a =
      sin(dLat / 2) * sin(dLat / 2) +
        cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLon / 2) * sin(dLon / 2)
    val c = 2 * atan2(sqrt(a), sqrt(1 - a))
    val distanceKm = earthRadiusKm * c
    return distanceKm * 1000.0
  }
}
