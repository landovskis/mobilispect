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
import com.mobilispect.backend.route.domain.repository.RouteRepository
import com.mobilispect.backend.route.domain.repository.RouteVariantRepository
import com.mobilispect.backend.route.domain.service.VariantIdentificationService
import com.mobilispect.backend.route.events.RouteVariantIdentified
import org.slf4j.LoggerFactory
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Component

/**
 * Handler that processes route variant data from GTFS feeds.
 *
 * This handler:
 * 1. Maps GTFS routes to persisted Route entities
 * 2. Identifies unique stop patterns per route
 * 3. Persists variants and publishes RouteVariantIdentified events
 *
 * Priority is set to 4 (after routes at 5) because variants depend on routes.
 */
@Component
class RouteVariantFeedDataHandler(
  private val routeRepository: RouteRepository,
  private val routeVariantRepository: RouteVariantRepository,
  private val variantIdentificationService: VariantIdentificationService,
  private val eventPublisher: ApplicationEventPublisher,
) : FeedDataHandler {

  private val logger = LoggerFactory.getLogger(RouteVariantFeedDataHandler::class.java)

  override fun dataTypes(): Set<GTFSDataType> =
    setOf(GTFSDataType.ROUTE, GTFSDataType.TRIP, GTFSDataType.STOP, GTFSDataType.SHAPE)

  override fun priority(): Int = 4

  override fun handle(feedId: FeedId, data: GTFSDataBundle, context: ImportContext): ImportResult {
    if (data.routes.isEmpty() || data.trips.isEmpty()) {
      logger.debug("No route variants to process for feed {}", feedId.value)
      return ImportResult.Success(0)
    }

    val stopsById = data.stops.associateBy { it.stopId.value }
    val tripsByRouteId = data.trips.groupBy { it.routeId }
    val routesWithTrips =
      data.routes.filter { route -> tripsByRouteId[route.routeId]?.isNotEmpty() == true }

    if (routesWithTrips.isEmpty()) {
      logger.debug("No route variants to process for feed {}", feedId.value)
      return ImportResult.Success(0)
    }

    logger.info(
      "Processing route variants for {} routes ({} trips) for feed {}",
      routesWithTrips.size,
      data.trips.size,
      feedId.value,
    )

    var successCount = 0
    val errors = mutableListOf<ImportError>()

    routesWithTrips.forEach { gtfsRoute ->
      val routeTrips = tripsByRouteId[gtfsRoute.routeId].orEmpty()
      val agencyId = gtfsRoute.agencyId ?: FeedLocalAgencyId("default-agency")
      val routeId = RouteId(AgencyId(feedId, agencyId), gtfsRoute.routeId)

      val route = routeRepository.findById(routeId)
      if (route == null) {
        logger.error("Failed to resolve route {} for feed {}", routeId.value, feedId.value)
        errors.add(
          ImportError(recordId = gtfsRoute.routeId.value, message = "Route not found in database")
        )
        return@forEach
      }

      try {
        val variants =
          variantIdentificationService.identifyVariants(route, routeTrips, stopsById, data.shapes)

        variants.forEach { variant ->
          val existing = routeVariantRepository.findById(variant.id)
          val saved =
            if (existing != null) {
              routeVariantRepository.save(
                existing.copy(
                  lastSeen = variant.lastSeen,
                  headsign = variant.headsign,
                  active = true,
                )
              )
            } else {
              routeVariantRepository.save(variant)
            }

          eventPublisher.publishEvent(
            RouteVariantIdentified(variantId = saved.id, routeId = saved.routeId)
          )

          successCount++
        }
      } catch (e: Exception) {
        logger.error(
          "Failed to process variants for route {} in feed {}: {}",
          routeId.value,
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
          "Successfully processed {} route variants for feed {}",
          successCount,
          feedId.value,
        )
        ImportResult.Success(successCount)
      }
      successCount > 0 -> {
        logger.warn(
          "Partially processed route variants for feed {}: {} succeeded, {} failed",
          feedId.value,
          successCount,
          errors.size,
        )
        ImportResult.PartialSuccess(successCount, errors)
      }
      else -> {
        logger.error("Failed to process any route variants for feed {}", feedId.value)
        ImportResult.Failure(errors.first())
      }
    }
  }
}
