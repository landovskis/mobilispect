package com.mobilispect.backend.route.batch.variant

import com.mobilispect.backend.route.domain.repository.RouteVariantRepository
import com.mobilispect.backend.route.events.RouteVariantIdentified
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
 * Spring Batch ItemWriter that persists route variants to the database.
 *
 * This writer:
 * 1. Receives chunks of RouteVariantBatch items from the processor
 * 2. For each variant, checks if it already exists in the database
 * 3. Creates new variants or updates existing ones (updating lastSeen timestamp)
 * 4. Publishes RouteVariantIdentified domain events for each variant
 * 5. Tracks and logs statistics about variants created/updated
 *
 * The writer is transactional - all variants in a chunk are saved atomically.
 * If any error occurs, the entire chunk is rolled back.
 *
 * Constitutional Requirements:
 * - Event-driven architecture: Publishes domain events for variant identification
 * - Module boundaries: Uses repository interface, not direct JPA access
 */
@Component
@StepScope
class RouteVariantWriter(
    private val routeVariantRepository: RouteVariantRepository,
    private val eventPublisher: ApplicationEventPublisher
) : ItemWriter<RouteVariantBatch> {

    private val logger = LoggerFactory.getLogger(RouteVariantWriter::class.java)

    private var stepExecution: StepExecution? = null
    private var cumulativeVariantsProcessed = 0
    private var cumulativeVariantsCreated = 0
    private var cumulativeVariantsUpdated = 0

    @BeforeStep
    fun beforeStep(stepExecution: StepExecution) {
        this.stepExecution = stepExecution
    }

    @Transactional
    override fun write(chunk: Chunk<out RouteVariantBatch>) {
        val batches = chunk.items

        logger.info("Writing {} route variant batches to database", batches.size)

        var chunkVariantsProcessed = 0
        var chunkVariantsCreated = 0
        var chunkVariantsUpdated = 0

        for ((batchIndex, batch) in batches.withIndex()) {
            logger.debug(
                "  Batch {}/{}: Processing {} variants",
                batchIndex + 1,
                batches.size,
                batch.variants.size
            )

            batch.variants.forEach { variant ->
                val existing = routeVariantRepository.findById(variant.id)

                val saved = if (existing != null) {
                    // Update existing variant - refresh lastSeen timestamp
                    chunkVariantsUpdated++
                    logger.debug(
                        "    Updating variant {} (route {}, {} stops)",
                        variant.id.value.take(12),
                        variant.routeId.value,
                        variant.stopCount
                    )
                    routeVariantRepository.save(
                        existing.copy(
                            lastSeen = variant.lastSeen,
                            headsign = variant.headsign,
                            active = true
                        )
                    )
                } else {
                    // Create new variant
                    chunkVariantsCreated++
                    logger.info(
                        "    ✓ Creating variant {} → {} (route {}, {} stops, direction {})",
                        variant.id.value.take(12),
                        variant.headsign ?: "unnamed",
                        variant.routeId.value,
                        variant.stopCount,
                        variant.directionId ?: "?"
                    )
                    routeVariantRepository.save(variant)
                }

                // Publish domain event
                eventPublisher.publishEvent(
                    RouteVariantIdentified(
                        variantId = saved.id,
                        routeId = saved.routeId
                    )
                )

                chunkVariantsProcessed++
            }
        }

        // Update cumulative statistics
        cumulativeVariantsProcessed += chunkVariantsProcessed
        cumulativeVariantsCreated += chunkVariantsCreated
        cumulativeVariantsUpdated += chunkVariantsUpdated

        logger.info(
            """
            ✓ Chunk complete:
              • Variants: {} total ({} created, {} updated)
              • Cumulative: {} total ({} created, {} updated)
            """.trimIndent(),
            chunkVariantsProcessed,
            chunkVariantsCreated,
            chunkVariantsUpdated,
            cumulativeVariantsProcessed,
            cumulativeVariantsCreated,
            cumulativeVariantsUpdated
        )

        // Record metrics in step execution context
        recordMetrics(chunkVariantsProcessed, chunkVariantsCreated, chunkVariantsUpdated)
    }

    private fun recordMetrics(processed: Int, created: Int, updated: Int) {
        if (processed == 0) {
            return
        }

        val context = stepExecution?.executionContext
        context?.putInt("variantsProcessed", cumulativeVariantsProcessed)
        context?.putInt("variantsCreated", cumulativeVariantsCreated)
        context?.putInt("variantsUpdated", cumulativeVariantsUpdated)

        stepExecution?.jobExecution?.executionContext?.putInt("variantsProcessed", cumulativeVariantsProcessed)
        stepExecution?.jobExecution?.executionContext?.putInt("variantsCreated", cumulativeVariantsCreated)
        stepExecution?.jobExecution?.executionContext?.putInt("variantsUpdated", cumulativeVariantsUpdated)
    }
}
