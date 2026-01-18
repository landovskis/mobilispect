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

  fun execute(stepExecution: StepExecution, feedOnestopId: String) {
    val parsedData =
      stepExecution.jobExecution.executionContext.get("parsedData") as? GTFSData
        ?: throw IllegalStateException("ParsedGtfsData not found in job execution context")

    val routesByFeedLocalId = mutableMapOf<String, Route>()
    var routesProcessed = 0
    var routesCreated = 0
    var routesUpdated = 0

    parsedData.routes.forEach { parsedRoute ->
      val gtfsAgencyId = parsedRoute.agencyId ?: FeedLocalAgencyId("default-agency")
      val agencyId = AgencyId(FeedId(feedOnestopId), gtfsAgencyId)
      val route =
        Route(
          id = RouteId(agencyId, parsedRoute.routeId),
          agencyId = agencyId,
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

      eventPublisher.publishEvent(RouteImported(routeId = saved.id))
      routesByFeedLocalId[parsedRoute.routeId.value] = saved
      routesProcessed++
    }

    logger.info(
      "Persisted routes for feed {} (total={}, created={}, updated={})",
      feedOnestopId,
      routesProcessed,
      routesCreated,
      routesUpdated,
    )

    val stepContext = stepExecution.executionContext
    stepContext.putInt("routesProcessed", routesProcessed)
    stepContext.putInt("routesCreated", routesCreated)
    stepContext.putInt("routesUpdated", routesUpdated)

    val jobContext = stepExecution.jobExecution.executionContext
    jobContext.putInt("routesProcessed", routesProcessed)
    jobContext.putInt("routesCreated", routesCreated)
    jobContext.putInt("routesUpdated", routesUpdated)
    jobContext.put("routesByFeedLocalId", routesByFeedLocalId)
  }
}
