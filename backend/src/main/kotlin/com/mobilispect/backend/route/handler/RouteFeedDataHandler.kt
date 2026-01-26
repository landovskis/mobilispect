package com.mobilispect.backend.route.handler

import com.mobilispect.backend.agency.AgencyId
import com.mobilispect.backend.feed.api.handler.FeedDataHandler
import com.mobilispect.backend.feed.api.handler.GTFSDataBundle
import com.mobilispect.backend.feed.api.handler.GTFSDataType
import com.mobilispect.backend.feed.api.handler.ImportContext
import com.mobilispect.backend.feed.api.handler.ImportError
import com.mobilispect.backend.feed.api.handler.ImportResult
import com.mobilispect.backend.feed.api.ids.FeedLocalAgencyId
import com.mobilispect.backend.feed.domain.model.ids.FeedId
import com.mobilispect.backend.route.RouteId
import com.mobilispect.backend.route.domain.model.Route
import com.mobilispect.backend.route.domain.model.RouteType
import com.mobilispect.backend.route.domain.repository.RouteRepository
import com.mobilispect.backend.route.events.RouteImported
import org.slf4j.LoggerFactory
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Component

/**
 * Handler that processes route data from GTFS feeds.
 *
 * This handler:
 * 1. Maps GTFS routes to domain Route entities
 * 2. Constructs route IDs from agency ID and GTFS route ID
 * 3. Updates existing routes or creates new ones
 * 4. Publishes RouteImported events for downstream processing
 *
 * Priority is set to 5 (after agencies at 10) because routes reference agencies.
 *
 * @param routeRepository Repository for persisting route entities
 * @param eventPublisher Spring event publisher for RouteImported events
 */
@Component
class RouteFeedDataHandler(
  private val routeRepository: RouteRepository,
  private val eventPublisher: ApplicationEventPublisher,
) : FeedDataHandler {

  private val logger = LoggerFactory.getLogger(RouteFeedDataHandler::class.java)

  override fun dataTypes(): Set<GTFSDataType> = setOf(GTFSDataType.ROUTE)

  override fun priority(): Int = 5

  override fun handle(feedId: FeedId, data: GTFSDataBundle, context: ImportContext): ImportResult {
    val routes = data.routes
    if (routes.isEmpty()) {
      logger.debug("No routes to process for feed {}", feedId.value)
      return ImportResult.Success(0)
    }

    logger.info("Processing {} routes for feed {}", routes.size, feedId.value)

    var successCount = 0
    var createdCount = 0
    var updatedCount = 0
    val errors = mutableListOf<ImportError>()

    routes.forEach { gtfsRoute ->
      try {
        val gtfsAgencyId = gtfsRoute.agencyId ?: FeedLocalAgencyId("default-agency")
        val agencyId = AgencyId(feedId, gtfsAgencyId)
        val routeId = RouteId(agencyId, gtfsRoute.routeId)

        val existingRoute = routeRepository.findById(routeId)

        val route =
          if (existingRoute != null) {
            // Update existing route
            updatedCount++
            existingRoute.copy(
              shortName = gtfsRoute.shortName,
              longName = gtfsRoute.longName ?: gtfsRoute.shortName ?: gtfsRoute.routeId.value,
              routeType = RouteType.fromGtfsValue(gtfsRoute.type ?: 3),
              active = true,
            )
          } else {
            // Create new route
            createdCount++
            Route(
              id = routeId,
              agencyId = agencyId,
              shortName = gtfsRoute.shortName,
              longName = gtfsRoute.longName ?: gtfsRoute.shortName ?: gtfsRoute.routeId.value,
              routeType = RouteType.fromGtfsValue(gtfsRoute.type ?: 3),
              active = true,
            )
          }

        val saved = routeRepository.save(route)
        successCount++

        // Publish domain event
        eventPublisher.publishEvent(RouteImported(routeId = saved.id))

        logger.debug(
          "Saved route {} ({}) for feed {}",
          gtfsRoute.shortName ?: gtfsRoute.routeId.value,
          gtfsRoute.longName,
          feedId.value,
        )
      } catch (e: Exception) {
        logger.error(
          "Failed to save route {} for feed {}: {}",
          gtfsRoute.routeId.value,
          feedId.value,
          e.message,
        )
        errors.add(
          ImportError(
            recordId = gtfsRoute.routeId.value,
            message = e.message ?: "Unknown error",
            exception = e,
          )
        )
      }
    }

    return when {
      errors.isEmpty() -> {
        logger.info(
          "Successfully processed {} routes for feed {} ({} created, {} updated)",
          successCount,
          feedId.value,
          createdCount,
          updatedCount,
        )
        ImportResult.Success(successCount)
      }
      successCount > 0 -> {
        logger.warn(
          "Partially processed routes for feed {}: {} succeeded, {} failed",
          feedId.value,
          successCount,
          errors.size,
        )
        ImportResult.PartialSuccess(successCount, errors)
      }
      else -> {
        logger.error("Failed to process any routes for feed {}", feedId.value)
        ImportResult.Failure(errors.first())
      }
    }
  }
}
