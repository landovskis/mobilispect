package com.mobilispect.backend.route.batch.frequency

import com.mobilispect.backend.route.domain.model.Frequency
import com.mobilispect.backend.route.domain.model.ids.VariantHash
import com.mobilispect.backend.route.domain.repository.FrequencyRepository
import com.mobilispect.backend.route.events.FrequencyCalculationCompleted
import org.slf4j.LoggerFactory
import org.springframework.batch.core.step.StepExecution
import org.springframework.batch.core.annotation.BeforeStep
import org.springframework.batch.core.configuration.annotation.StepScope
import org.springframework.batch.infrastructure.item.Chunk
import org.springframework.batch.infrastructure.item.ItemWriter
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

/**
 * Spring Batch ItemWriter that persists frequency records to the database.
 *
 * This writer:
 * 1. Receives chunks of FrequencyBatch items from the processor
 * 2. For each frequency record, checks if it already exists in the database
 * 3. Creates new frequency records or updates existing ones
 * 4. Publishes FrequencyCalculationCompleted domain events for each variant
 * 5. Tracks and logs statistics about frequencies created/updated
 *
 * The writer is transactional - all frequencies in a chunk are saved atomically.
 * If any error occurs, the entire chunk is rolled back.
 *
 * Constitutional Requirements:
 * - Event-driven architecture: Publishes domain events for frequency calculation
 * - Module boundaries: Uses repository interface, not direct JPA access
 */
@Component
@StepScope
class FrequencyWriter(
    private val frequencyRepository: FrequencyRepository,
    private val eventPublisher: ApplicationEventPublisher
) : ItemWriter<FrequencyBatch> {

    private val logger = LoggerFactory.getLogger(FrequencyWriter::class.java)

    private var stepExecution: StepExecution? = null
    private var cumulativeFrequenciesProcessed = 0
    private var cumulativeFrequenciesCreated = 0
    private var cumulativeFrequenciesUpdated = 0

    @BeforeStep
    fun beforeStep(stepExecution: StepExecution) {
        this.stepExecution = stepExecution
    }

    @Transactional
    override fun write(chunk: Chunk<out FrequencyBatch>) {
        val batches = chunk.items

        logger.info("Writing {} frequency batches to database", batches.size)

        var chunkFrequenciesProcessed = 0
        var chunkFrequenciesCreated = 0
        var chunkFrequenciesUpdated = 0

        // Track which variants we've processed for event publishing
        val processedVariants = mutableSetOf<Pair<VariantHash, java.time.LocalDate>>()

        for ((batchIndex, batch) in batches.withIndex()) {
            logger.debug(
                "  Batch {}/{}: Processing {} frequency records",
                batchIndex + 1,
                batches.size,
                batch.frequencies.size
            )

            batch.frequencies.forEach { frequency ->
                val existing = frequencyRepository.findByVariantAndServiceDateAndTimePeriod(
                    variantId = frequency.variantId,
                    serviceDate = frequency.serviceDate,
                    timePeriod = frequency.timePeriod
                )

                val saved = if (existing.isPresent) {
                    // Update existing frequency
                    chunkFrequenciesUpdated++
                    val existingFrequency = existing.get()
                    logger.debug(
                        "    Updating frequency for variant {} on {} during {}",
                        frequency.variantId,
                        frequency.serviceDate,
                        frequency.timePeriod
                    )
                    frequencyRepository.save(
                        Frequency(
                            id = existingFrequency.id,
                            variantId = frequency.variantId,
                            serviceDate = frequency.serviceDate,
                            timePeriod = frequency.timePeriod,
                            averageHeadway = frequency.averageHeadway,
                            minHeadway = frequency.minHeadway,
                            maxHeadway = frequency.maxHeadway,
                            tripCount = frequency.tripCount,
                            isIrregular = frequency.isIrregular
                        )
                    )
                } else {
                    // Create new frequency
                    chunkFrequenciesCreated++
                    logger.info(
                        "    ✓ Creating frequency for variant {} on {} during {} (avg: {} min, trips: {})",
                        frequency.variantId,
                        frequency.serviceDate,
                        frequency.timePeriod,
                        frequency.averageHeadway?.let { "%.1f".format(it) } ?: "irregular",
                        frequency.tripCount
                    )
                    frequencyRepository.save(frequency)
                }

                // Track variant for event publishing
                processedVariants.add(VariantHash(saved.variantId) to saved.serviceDate)

                chunkFrequenciesProcessed++
            }
        }

        // Publish domain events for each variant
        processedVariants.forEach { (variantId, serviceDate) ->
            eventPublisher.publishEvent(
                FrequencyCalculationCompleted(
                    variantId = variantId,
                    serviceDate = serviceDate
                )
            )
        }

        // Update cumulative statistics
        cumulativeFrequenciesProcessed += chunkFrequenciesProcessed
        cumulativeFrequenciesCreated += chunkFrequenciesCreated
        cumulativeFrequenciesUpdated += chunkFrequenciesUpdated

        logger.info(
            """
            ✓ Chunk complete:
              • Frequencies: {} total ({} created, {} updated)
              • Cumulative: {} total ({} created, {} updated)
            """.trimIndent(),
            chunkFrequenciesProcessed,
            chunkFrequenciesCreated,
            chunkFrequenciesUpdated,
            cumulativeFrequenciesProcessed,
            cumulativeFrequenciesCreated,
            cumulativeFrequenciesUpdated
        )

        // Record metrics in step execution context
        recordMetrics(chunkFrequenciesProcessed, chunkFrequenciesCreated, chunkFrequenciesUpdated)
    }

    private fun recordMetrics(processed: Int, created: Int, updated: Int) {
        if (processed == 0) {
            return
        }

        val context = stepExecution?.executionContext
        context?.putInt("frequenciesProcessed", cumulativeFrequenciesProcessed)
        context?.putInt("frequenciesCreated", cumulativeFrequenciesCreated)
        context?.putInt("frequenciesUpdated", cumulativeFrequenciesUpdated)

        stepExecution?.jobExecution?.executionContext?.putInt("frequenciesProcessed", cumulativeFrequenciesProcessed)
        stepExecution?.jobExecution?.executionContext?.putInt("frequenciesCreated", cumulativeFrequenciesCreated)
        stepExecution?.jobExecution?.executionContext?.putInt("frequenciesUpdated", cumulativeFrequenciesUpdated)
    }
}
