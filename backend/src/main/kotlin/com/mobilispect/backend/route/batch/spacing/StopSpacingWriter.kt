package com.mobilispect.backend.route.batch.spacing

import com.mobilispect.backend.route.domain.repository.StopSpacingRepository
import org.slf4j.LoggerFactory
import org.springframework.batch.core.annotation.BeforeStep
import org.springframework.batch.core.configuration.annotation.StepScope
import org.springframework.batch.core.step.StepExecution
import org.springframework.batch.infrastructure.item.Chunk
import org.springframework.batch.infrastructure.item.ItemWriter
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

/**
 * Spring Batch ItemWriter that persists stop spacing records for route variants.
 *
 * This writer:
 * 1. Receives chunks of StopSpacingBatch items from the processor
 * 2. Persists individual StopSpacing records for each consecutive stop pair
 * 3. Tracks and logs statistics about spacing records created
 *
 * The writer is transactional - all spacing records in a chunk are saved atomically. If any error
 * occurs, the entire chunk is rolled back.
 *
 * Constitutional Requirements:
 * - Module boundaries: Uses repository interface, not direct JPA access
 */
@Component
@StepScope
class StopSpacingWriter(private val stopSpacingRepository: StopSpacingRepository) :
  ItemWriter<StopSpacingBatch> {

  private val logger = LoggerFactory.getLogger(StopSpacingWriter::class.java)

  private var stepExecution: StepExecution? = null
  private var cumulativeVariantsProcessed = 0
  private var cumulativeSpacingsCreated = 0

  @BeforeStep
  fun beforeStep(stepExecution: StepExecution) {
    this.stepExecution = stepExecution
  }

  @Transactional
  override fun write(chunk: Chunk<out StopSpacingBatch>) {
    val batches = chunk.items

    logger.info("Writing {} stop spacing batches to database", batches.size)

    var chunkVariantsProcessed = 0
    var chunkSpacingsCreated = 0

    for ((batchIndex, batch) in batches.withIndex()) {
      val spacings = batch.spacings

      if (spacings.isEmpty()) {
        logger.debug("  Batch {}/{}: Empty spacing batch, skipping", batchIndex + 1, batches.size)
        continue
      }

      val variantId = spacings.first().variantId

      logger.debug(
        "  Batch {}/{}: Processing {} spacing records for variant {}",
        batchIndex + 1,
        batches.size,
        spacings.size,
        variantId,
      )

      // Delete existing spacing records for this variant to avoid duplicates
      if (stopSpacingRepository.existsByVariant(variantId)) {
        logger.debug("    Deleting existing spacing records for variant {}", variantId)
        stopSpacingRepository.deleteByVariant(variantId)
      }

      // Save all spacing records for this variant
      val savedSpacings = stopSpacingRepository.saveAll(spacings)
      chunkSpacingsCreated += savedSpacings.count()

      val distances = spacings.map { it.distanceMeters }
      logger.info(
        "    ✓ Created {} spacing records for variant {} (avg: {} m, min: {} m, max: {} m)",
        spacings.size,
        variantId,
        "%.0f".format(distances.average()),
        "%.0f".format(distances.minOrNull() ?: 0.0),
        "%.0f".format(distances.maxOrNull() ?: 0.0),
      )

      chunkVariantsProcessed++
    }

    // Update cumulative statistics
    cumulativeVariantsProcessed += chunkVariantsProcessed
    cumulativeSpacingsCreated += chunkSpacingsCreated

    logger.info(
      """
            ✓ Chunk complete:
              • Variants: {} ({} spacing records)
              • Cumulative: {} variants ({} spacing records)
            """
        .trimIndent(),
      chunkVariantsProcessed,
      chunkSpacingsCreated,
      cumulativeVariantsProcessed,
      cumulativeSpacingsCreated,
    )

    // Record metrics in step execution context
    recordMetrics(chunkVariantsProcessed, chunkSpacingsCreated)
  }

  private fun recordMetrics(variantsProcessed: Int, spacingsCreated: Int) {
    if (variantsProcessed == 0) {
      return
    }

    val context = stepExecution?.executionContext
    context?.putInt("variantsProcessed", cumulativeVariantsProcessed)
    context?.putInt("spacingsCreated", cumulativeSpacingsCreated)

    stepExecution
      ?.jobExecution
      ?.executionContext
      ?.putInt("stopSpacingVariantsProcessed", cumulativeVariantsProcessed)
    stepExecution
      ?.jobExecution
      ?.executionContext
      ?.putInt("stopSpacingRecordsCreated", cumulativeSpacingsCreated)
  }
}
