package com.mobilispect.backend.route.handler

import com.mobilispect.backend.feed.api.handler.FeedDataHandler
import com.mobilispect.backend.feed.api.handler.GTFSDataBundle
import com.mobilispect.backend.feed.api.handler.GTFSDataType
import com.mobilispect.backend.feed.api.handler.ImportContext
import com.mobilispect.backend.feed.api.handler.ImportError
import com.mobilispect.backend.feed.api.handler.ImportResult
import com.mobilispect.backend.feed.domain.model.ids.FeedId
import com.mobilispect.backend.route.RouteId
import com.mobilispect.backend.route.domain.model.RouteCommonSection
import com.mobilispect.backend.route.domain.repository.RouteCommonSectionRepository
import com.mobilispect.backend.route.domain.repository.RouteVariantRepository
import com.mobilispect.backend.route.domain.service.RouteCommonSectionDetectionService
import java.security.MessageDigest
import java.time.Instant
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

/**
 * Handler that detects and persists the longest continuous section of stops
 * shared by ALL variants in each direction of a route.
 *
 * This handler:
 * 1. Groups variants by route and direction
 * 2. Uses RouteCommonSectionDetectionService to find the longest common section
 * 3. Persists RouteCommonSection records for each route/direction combination
 *
 * Priority is set to 1 (after classification at 2) to run near the end of import.
 */
@Component
class RouteCommonSectionFeedDataHandler(
  private val routeVariantRepository: RouteVariantRepository,
  private val routeCommonSectionRepository: RouteCommonSectionRepository,
  private val detectionService: RouteCommonSectionDetectionService,
) : FeedDataHandler {

  private val logger = LoggerFactory.getLogger(RouteCommonSectionFeedDataHandler::class.java)

  override fun dataTypes(): Set<GTFSDataType> = emptySet() // No specific data types needed

  override fun priority(): Int = 1

  @Transactional
  override fun handle(feedId: FeedId, data: GTFSDataBundle, context: ImportContext): ImportResult {
    val variants = routeVariantRepository.findAll()

    if (variants.isEmpty()) {
      logger.debug("No route variants found for feed {}, skipping common section detection", feedId.value)
      return ImportResult.Success(0)
    }

    logger.info("Detecting common sections for {} variants in feed {}", variants.size, feedId.value)

    // Group variants by route and direction
    val variantsByRouteAndDirection = variants.groupBy { Pair(it.routeId, it.directionId) }

    var sectionsCreated = 0
    val errors = mutableListOf<ImportError>()

    variantsByRouteAndDirection.forEach { (key, routeVariants) ->
      val (routeId, directionId) = key

      try {
        // Detect common section for this route/direction
        val commonSectionResult = detectionService.detectCommonSection(routeVariants)

        if (commonSectionResult != null) {
          // Create RouteCommonSection domain model
          val stopIds = commonSectionResult.stopIds.split("|")
          val commonSection = RouteCommonSection(
            id = generateId(routeId, directionId, commonSectionResult.stopIds),
            routeId = routeId,
            directionId = directionId,
            stopPattern = commonSectionResult.stopIds,
            stopNamePattern = commonSectionResult.stopNames.joinToString("|"),
            stopCount = stopIds.size,
            firstStopId = stopIds.first(),
            lastStopId = stopIds.last(),
            variantCount = routeVariants.size,
            createdAt = Instant.now(),
            updatedAt = Instant.now(),
          )

          // Check if already exists
          val existing = routeCommonSectionRepository.findByRouteIdAndDirectionId(routeId, directionId)

          if (existing != null) {
            // Update if pattern changed
            if (existing.stopPattern != commonSection.stopPattern) {
              logger.debug(
                "Updating common section for route {} direction {}: {} stops",
                routeId.value,
                directionId ?: "null",
                commonSection.stopCount,
              )
              routeCommonSectionRepository.save(
                commonSection.copy(
                  createdAt = existing.createdAt,
                  updatedAt = Instant.now(),
                )
              )
              sectionsCreated++
            }
          } else {
            // Create new
            logger.debug(
              "Creating common section for route {} direction {}: {} stops",
              routeId.value,
              directionId ?: "null",
              commonSection.stopCount,
            )
            routeCommonSectionRepository.save(commonSection)
            sectionsCreated++
          }
        } else {
          logger.trace(
            "No common section found for route {} direction {} ({} variants)",
            routeId.value,
            directionId ?: "null",
            routeVariants.size,
          )
        }
      } catch (e: Exception) {
        logger.error(
          "Failed to detect common section for route {} direction {}: {}",
          routeId.value,
          directionId ?: "null",
          e.message,
        )
        errors.add(
          ImportError(
            recordId = "${routeId.value}_${directionId ?: "null"}",
            message = e.message ?: "Unknown error",
            exception = e,
          )
        )
      }
    }

    logger.info(
      "Processed common sections for {} route/direction combinations in feed {} ({} created/updated)",
      variantsByRouteAndDirection.size,
      feedId.value,
      sectionsCreated,
    )

    return when {
      errors.isEmpty() -> ImportResult.Success(sectionsCreated)
      sectionsCreated > 0 -> ImportResult.PartialSuccess(sectionsCreated, errors)
      else -> ImportResult.Failure(errors.first())
    }
  }

  /**
   * Generates a deterministic ID (SHA-256 hash) for a route common section.
   */
  private fun generateId(routeId: RouteId, directionId: Int?, stopPattern: String): String {
    val input = "${routeId.value}_${directionId ?: "null"}_$stopPattern"
    val digest = MessageDigest.getInstance("SHA-256")
    val hashBytes = digest.digest(input.toByteArray())
    return hashBytes.joinToString("") { "%02x".format(it) }
  }
}
