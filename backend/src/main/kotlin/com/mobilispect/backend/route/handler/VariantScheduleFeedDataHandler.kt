package com.mobilispect.backend.route.handler

import com.mobilispect.backend.feed.api.GTFSTrip
import com.mobilispect.backend.feed.api.handler.FeedDataHandler
import com.mobilispect.backend.feed.api.handler.GTFSDataBundle
import com.mobilispect.backend.feed.api.handler.GTFSDataType
import com.mobilispect.backend.feed.api.handler.ImportContext
import com.mobilispect.backend.feed.api.handler.ImportError
import com.mobilispect.backend.feed.api.handler.ImportResult
import com.mobilispect.backend.feed.domain.model.ids.FeedId
import com.mobilispect.backend.route.domain.model.VariantSchedule
import com.mobilispect.backend.route.domain.repository.VariantScheduleRepository
import com.mobilispect.backend.route.domain.service.VariantHashGenerator
import java.time.Instant
import java.time.LocalTime
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

/**
 * Handler that calculates and persists schedule summaries for route variants.
 *
 * This handler:
 * 1. Groups trips by variant (using stop pattern hash)
 * 2. Extracts first departure times from each trip
 * 3. Calculates earliest/latest departure times and trip count per variant
 * 4. Persists VariantSchedule records
 *
 * Priority is set to 2 (after route variants at 4 and stop spacing at 3) because schedules depend
 * on variants.
 *
 * @param variantScheduleRepository Repository for persisting variant schedule entities
 * @param variantHashGenerator Generator for computing variant hashes
 */
@Component
class VariantScheduleFeedDataHandler(
  private val variantScheduleRepository: VariantScheduleRepository,
  private val variantHashGenerator: VariantHashGenerator,
) : FeedDataHandler {

  private val logger = LoggerFactory.getLogger(VariantScheduleFeedDataHandler::class.java)

  override fun dataTypes(): Set<GTFSDataType> = setOf(GTFSDataType.TRIP)

  override fun priority(): Int = 2

  @Transactional
  override fun handle(feedId: FeedId, data: GTFSDataBundle, context: ImportContext): ImportResult {
    if (data.trips.isEmpty()) {
      logger.debug("No trips in bundle for feed {}, skipping schedule calculation", feedId.value)
      return ImportResult.Success(0)
    }

    logger.info(
      "Calculating variant schedules for {} trips for feed {}",
      data.trips.size,
      feedId.value,
    )

    // Group trips by variant
    val tripsByVariant = groupTripsByVariant(data.trips)

    if (tripsByVariant.isEmpty()) {
      logger.debug("No valid variants found for feed {}", feedId.value)
      return ImportResult.Success(0)
    }

    var variantsProcessed = 0
    val errors = mutableListOf<ImportError>()

    tripsByVariant.forEach { (variantId, trips) ->
      try {
        val schedule = calculateScheduleSummary(variantId.value, trips)

        if (schedule != null) {
          // Delete existing schedule for this variant to avoid duplicates
          if (variantScheduleRepository.existsByVariantId(variantId.value)) {
            logger.debug("Deleting existing schedule for variant {}", variantId.value.take(12))
            variantScheduleRepository.deleteByVariantId(variantId.value)
          }

          // Save schedule summary
          variantScheduleRepository.save(schedule)

          logger.debug(
            "Created schedule for variant {} (first: {}, last: {}, trips: {})",
            variantId.value.take(12),
            schedule.firstDepartureTime,
            schedule.lastDepartureTime,
            schedule.tripCount,
          )

          variantsProcessed++
        } else {
          logger.debug(
            "Variant {} has no valid departure times, skipping",
            variantId.value.take(12),
          )
        }
      } catch (e: Exception) {
        logger.error(
          "Failed to calculate schedule for variant {}: {}",
          variantId.value.take(12),
          e.message,
        )
        errors.add(
          ImportError(
            recordId = variantId.value,
            message = e.message ?: "Unknown error",
            exception = e,
          )
        )
      }
    }

    logger.info("Processed schedules for {} variants for feed {}", variantsProcessed, feedId.value)

    return when {
      errors.isEmpty() -> ImportResult.Success(variantsProcessed)
      variantsProcessed > 0 -> ImportResult.PartialSuccess(variantsProcessed, errors)
      else -> ImportResult.Failure(errors.first())
    }
  }

  /**
   * Group trips by variant hash.
   *
   * @param trips List of GTFS trips
   * @return Map of variant hash to trips
   */
  private fun groupTripsByVariant(trips: List<GTFSTrip>): Map<com.mobilispect.backend.route.domain.model.ids.VariantHash, List<GTFSTrip>> {
    val tripsByVariant =
      mutableMapOf<com.mobilispect.backend.route.domain.model.ids.VariantHash, MutableList<GTFSTrip>>()

    trips.forEach { trip ->
      val stopIds = trip.stopTimes.sortedBy { it.stopSequence }.map { it.stopId.value }
      if (stopIds.size < 2) return@forEach

      val variantHash = variantHashGenerator.fromStops(stopIds)

      tripsByVariant.getOrPut(variantHash) { mutableListOf() }.add(trip)
    }

    return tripsByVariant
  }

  /**
   * Calculate schedule summary for a variant.
   *
   * @param variantId The variant ID
   * @param trips List of trips for this variant
   * @return VariantSchedule if valid departure times exist, null otherwise
   */
  private fun calculateScheduleSummary(variantId: String, trips: List<GTFSTrip>): VariantSchedule? {
    // Extract first departure time from each trip (departure from first stop)
    val departureTimes =
      trips
        .mapNotNull { trip -> trip.stopTimes.firstOrNull()?.departureTime }
        .filter { it != LocalTime.MIDNIGHT } // Filter out invalid midnight times if needed

    if (departureTimes.isEmpty()) {
      return null
    }

    val firstDeparture = departureTimes.minOrNull() ?: return null
    val lastDeparture = departureTimes.maxOrNull() ?: return null

    return VariantSchedule(
      variantId = variantId,
      firstDepartureTime = firstDeparture,
      lastDepartureTime = lastDeparture,
      tripCount = trips.size,
      calculatedAt = Instant.now(),
    )
  }
}
