package com.mobilispect.backend.route.handler

import com.mobilispect.backend.feed.api.handler.FeedDataHandler
import com.mobilispect.backend.feed.api.handler.GTFSDataBundle
import com.mobilispect.backend.feed.api.handler.GTFSDataType
import com.mobilispect.backend.feed.api.handler.ImportContext
import com.mobilispect.backend.feed.api.handler.ImportError
import com.mobilispect.backend.feed.api.handler.ImportResult
import com.mobilispect.backend.feed.domain.model.ids.FeedId
import com.mobilispect.backend.route.domain.model.RouteClassification
import com.mobilispect.backend.route.domain.repository.RouteVariantRepository
import com.mobilispect.backend.route.domain.repository.StopSpacingRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

/**
 * Handler that classifies route variants based on their average stop spacing.
 *
 * This handler:
 * 1. Fetches all persisted route variants from the database
 * 2. Calculates average stop spacing from StopSpacing records
 * 3. Classifies each variant using RouteClassification thresholds
 * 4. Updates RouteVariant with classification and average spacing
 *
 * Priority is set to 2 (after stop spacing at 3) because classification depends on spacing data.
 *
 * @param stopSpacingRepository Repository for querying stop spacing data
 * @param routeVariantRepository Repository for fetching and updating route variants
 */
@Component
class RouteClassificationFeedDataHandler(
  private val stopSpacingRepository: StopSpacingRepository,
  private val routeVariantRepository: RouteVariantRepository,
) : FeedDataHandler {

  private val logger = LoggerFactory.getLogger(RouteClassificationFeedDataHandler::class.java)

  override fun dataTypes(): Set<GTFSDataType> = setOf(GTFSDataType.STOP)

  override fun priority(): Int = 2

  @Transactional
  override fun handle(feedId: FeedId, data: GTFSDataBundle, context: ImportContext): ImportResult {
    val variants = routeVariantRepository.findAll()

    if (variants.isEmpty()) {
      logger.debug("No route variants found for feed {}, skipping classification", feedId.value)
      return ImportResult.Success(0)
    }

    logger.info("Classifying {} route variants for feed {}", variants.size, feedId.value)

    var variantsUpdated = 0
    val errors = mutableListOf<ImportError>()

    variants.forEach { variant ->
      try {
        val averageSpacing = stopSpacingRepository.calculateAverageByVariant(variant.id.value)
        val classification = RouteClassification.fromAverageSpacing(averageSpacing)

        // Only update if classification or spacing changed
        if (
          variant.classification != classification ||
            variant.averageStopSpacingMeters != averageSpacing
        ) {
          val updatedVariant =
            variant.copy(classification = classification, averageStopSpacingMeters = averageSpacing)

          routeVariantRepository.save(updatedVariant)

          logger.debug(
            "Classified variant {} as {} (avg spacing: {} m)",
            variant.id.value.take(12),
            classification,
            averageSpacing?.let { "%.0f".format(it) } ?: "N/A",
          )

          variantsUpdated++
        } else {
          logger.trace(
            "Variant {} classification unchanged: {}",
            variant.id.value.take(12),
            classification,
          )
        }
      } catch (e: Exception) {
        logger.error("Failed to classify variant {}: {}", variant.id.value.take(12), e.message)
        errors.add(
          ImportError(
            recordId = variant.id.value,
            message = e.message ?: "Unknown error",
            exception = e,
          )
        )
      }
    }

    logger.info(
      "Classified {} route variants for feed {} ({} updated)",
      variants.size,
      feedId.value,
      variantsUpdated,
    )

    return when {
      errors.isEmpty() -> ImportResult.Success(variantsUpdated)
      variantsUpdated > 0 -> ImportResult.PartialSuccess(variantsUpdated, errors)
      else -> ImportResult.Failure(errors.first())
    }
  }
}
