package com.mobilispect.backend.route.batch.spacing

import com.mobilispect.backend.feed.gtfs.ParsedGtfsData
import com.mobilispect.backend.feed.gtfs.ParsedStop
import com.mobilispect.backend.route.domain.model.RouteVariant
import com.mobilispect.backend.route.domain.repository.RouteVariantRepository
import org.slf4j.LoggerFactory
import org.springframework.batch.core.step.StepExecution
import org.springframework.batch.core.annotation.BeforeStep
import org.springframework.batch.core.configuration.annotation.StepScope
import org.springframework.batch.infrastructure.item.ItemReader
import org.springframework.stereotype.Component

/**
 * Spring Batch ItemReader that reads persisted route variants and produces StopSpacingInput items.
 *
 * This reader:
 * 1. Retrieves ParsedGtfsData from the job execution context
 * 2. Fetches persisted RouteVariant entities from the database
 * 3. Creates StopSpacingInput combining:
 *    - Persisted RouteVariant entity
 *    - Stop location data from GTFS
 * 4. Returns one StopSpacingInput per variant for processing
 *
 * The reader processes variants sequentially, yielding one StopSpacingInput at a time.
 * This allows the batch framework to chunk the processing and apply transaction boundaries.
 */
@Component
@StepScope
class StopSpacingReader(
    private val routeVariantRepository: RouteVariantRepository
) : ItemReader<StopSpacingInput> {

    private val logger = LoggerFactory.getLogger(StopSpacingReader::class.java)

    private var parsedData: ParsedGtfsData? = null
    private var variantIterator: Iterator<RouteVariant>? = null
    private var stopsById: Map<String, ParsedStop> = emptyMap()

    @BeforeStep
    fun beforeStep(stepExecution: StepExecution) {
        // Retrieve parsed data from job execution context
        parsedData = stepExecution.jobExecution.executionContext.get("parsedData") as? ParsedGtfsData
            ?: throw IllegalStateException("ParsedGtfsData not found in job execution context")

        val data = parsedData!!

        logger.info(
            "Initializing StopSpacingReader with {} stops from GTFS data",
            data.stops.size
        )

        // Create stop lookup map
        stopsById = data.stops.associateBy { it.stopId }

        // Fetch all persisted route variants from database
        val persistedVariants = routeVariantRepository.findAll()
        logger.info("Fetched {} persisted route variants from database", persistedVariants.size)

        variantIterator = persistedVariants.iterator()

        logger.info("Prepared {} variants for stop spacing calculation", persistedVariants.size)
    }

    override fun read(): StopSpacingInput? {
        if (variantIterator == null || !variantIterator!!.hasNext()) {
            return null
        }

        val variant = variantIterator!!.next()

        return StopSpacingInput(
            variant = variant,
            stopsById = stopsById
        )
    }
}
