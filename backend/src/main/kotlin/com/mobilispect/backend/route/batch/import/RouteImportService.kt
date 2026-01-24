package com.mobilispect.backend.route.batch.import

import com.mobilispect.backend.agency.AgencyId
import com.mobilispect.backend.feed.api.GTFSData
import com.mobilispect.backend.feed.api.ids.FeedLocalAgencyId
import com.mobilispect.backend.feed.domain.model.ids.FeedId
import com.mobilispect.backend.route.RouteId
import com.mobilispect.backend.route.domain.model.Route
import com.mobilispect.backend.route.domain.model.RouteType
import com.mobilispect.backend.route.domain.repository.RouteRepository
import com.mobilispect.backend.route.events.RouteImported
import org.slf4j.LoggerFactory
import org.springframework.batch.core.step.StepExecution
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Service

@Service
class RouteImportService(
  private val routeRepository: RouteRepository,
  private val eventPublisher: ApplicationEventPublisher,
) {
  private val logger = LoggerFactory.getLogger(RouteImportService::class.java)

  /**
   * Execute route import from Spring Batch step execution context.
   *
   * Delegates to [processRoutes] after extracting parsed data from the context.
   */
  fun execute(stepExecution: StepExecution, feedOnestopId: String) {
    val parsedData =
      stepExecution.jobExecution.executionContext.get("parsedData") as? GTFSData
        ?: throw IllegalStateException("ParsedGtfsData not found in job execution context")

    val routesByFeedLocalId = processRoutes(FeedId(feedOnestopId), parsedData)

    // Store results in execution context for downstream steps
    val stepContext = stepExecution.executionContext
    stepContext.putInt("routesProcessed", routesByFeedLocalId.size)

    val jobContext = stepExecution.jobExecution.executionContext
    jobContext.putInt("routesProcessed", routesByFeedLocalId.size)
    jobContext.put("routesByFeedLocalId", routesByFeedLocalId)
  }

  /**
   * Process routes from parsed GTFS data.
   *
   * This method can be called directly for synchronous processing or from Spring Batch.
   *
   * @param feedId The feed being imported
   * @param parsedData Parsed GTFS data containing routes
   * @return Map of feed-local route ID to persisted Route entity
   */
  fun processRoutes(feedId: FeedId, parsedData: GTFSData): Map<String, Route> {
    val routesByFeedLocalId = mutableMapOf<String, Route>()
    var routesCreated = 0
    var routesUpdated = 0

    parsedData.routes.forEach { parsedRoute ->
      val gtfsAgencyId = parsedRoute.agencyId ?: FeedLocalAgencyId("default-agency")
      val agencyId = AgencyId(feedId, gtfsAgencyId)
      val route =
        Route(
          id = RouteId(agencyId, parsedRoute.routeId),
          agencyId = agencyId,
          gtfsRouteId = parsedRoute.routeId.value,
          shortName = parsedRoute.shortName,
          longName = parsedRoute.longName ?: parsedRoute.shortName ?: parsedRoute.routeId.value,
          routeType = RouteType.fromGtfsValue(parsedRoute.type ?: 3),
          color = null,
          textColor = null,
          active = true,
        )

      val existing = routeRepository.findById(route.id)
      val saved =
        if (existing != null) {
          routesUpdated++
          routeRepository.save(
            existing.copy(
              shortName = route.shortName,
              longName = route.longName,
              routeType = route.routeType,
              color = route.color,
              textColor = route.textColor,
              active = true,
            )
          )
        } else {
          routesCreated++
          routeRepository.save(route)
        }

      eventPublisher.publishEvent(
        RouteImported(routeId = saved.id, gtfsRouteId = saved.gtfsRouteId)
      )
      routesByFeedLocalId[parsedRoute.routeId.value] = saved
    }

    logger.info(
      "Persisted routes for feed {} (total={}, created={}, updated={})",
      feedId.value,
      routesByFeedLocalId.size,
      routesCreated,
      routesUpdated,
    )

    return routesByFeedLocalId
  }
}
