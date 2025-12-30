package com.mobilispect.backend.route.batch.frequency

import com.mobilispect.backend.feed.api.GTFSData
import com.mobilispect.backend.feed.api.GTFSTrip
import com.mobilispect.backend.route.domain.model.RouteVariant
import com.mobilispect.backend.route.domain.repository.RouteVariantRepository
import org.slf4j.LoggerFactory
import org.springframework.batch.core.annotation.BeforeStep
import org.springframework.batch.core.configuration.annotation.StepScope
import org.springframework.batch.core.step.StepExecution
import org.springframework.batch.infrastructure.item.ItemReader
import org.springframework.stereotype.Component

/**
 * Spring Batch ItemReader that reads persisted route variants and produces FrequencyInput items.
 *
 * This reader:
 * 1. Retrieves ParsedGtfsData from the job execution context
 * 2. Fetches persisted RouteVariant entities from the database
 * 3. Matches variants to their trips using stop pattern
 * 4. For each variant with trips, creates a FrequencyInput combining:
 *     - Persisted RouteVariant entity
 *     - Trips belonging to that variant
 * 5. Returns one FrequencyInput per variant for processing
 *
 * The reader processes variants sequentially, yielding one FrequencyInput at a time. This allows
 * the batch framework to chunk the processing and apply transaction boundaries.
 */
@Component
@StepScope
class FrequencyReader(private val routeVariantRepository: RouteVariantRepository) :
  ItemReader<FrequencyInput> {

  private val logger = LoggerFactory.getLogger(FrequencyReader::class.java)

  private var parsedData: GTFSData? = null
  private var variantIterator: Iterator<Map.Entry<RouteVariant, List<GTFSTrip>>>? = null

  @BeforeStep
  fun beforeStep(stepExecution: StepExecution) {
    // Retrieve parsed data from job execution context
    parsedData =
      stepExecution.jobExecution.executionContext.get("parsedData") as? GTFSData
        ?: throw IllegalStateException("ParsedGtfsData not found in job execution context")

    val data = parsedData!!

    logger.info("Initializing FrequencyReader with {} trips from GTFS data", data.trips.size)

    // Fetch all persisted route variants from database
    val persistedVariants = routeVariantRepository.findAll()
    logger.info("Fetched {} persisted route variants from database", persistedVariants.size)

    // Match variants to their trips using stop pattern
    val variantMap =
      persistedVariants
        .associateWith { variant ->
          data.trips.filter { trip -> matchesTripPattern(trip, variant.stops) }
        }
        .filterValues { it.isNotEmpty() } // Only include variants with trips

    variantIterator = variantMap.entries.iterator()

    logger.info(
      "Prepared {} variants for frequency calculation ({} variants had no trips)",
      variantMap.size,
      persistedVariants.size - variantMap.size,
    )
  }

  override fun read(): FrequencyInput? {
    if (variantIterator == null || !variantIterator!!.hasNext()) {
      return null
    }

    val (variant, trips) = variantIterator!!.next()

    return FrequencyInput(variant = variant, trips = trips)
  }

  /**
   * Check if a trip matches a variant's stop pattern.
   *
   * Reconstructs trip's stop pattern from stop times and compares with variant's pattern.
   */
  private fun matchesTripPattern(trip: GTFSTrip, variantStops: List<String>): Boolean {
    val tripPattern = trip.stopTimes.sortedBy { it.stopSequence }.map { it.stopId.value }
    return tripPattern == variantStops
  }
}
