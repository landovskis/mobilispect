package com.mobilispect.backend.route.batch.import

import com.mobilispect.backend.agency.domain.repository.AgencyRepository
import com.mobilispect.backend.feed.api.ids.GTFSAgencyId
import com.mobilispect.backend.feed.domain.model.ids.FeedId
import com.mobilispect.backend.route.domain.model.Route
import com.mobilispect.backend.route.domain.model.RouteType
import com.mobilispect.backend.route.domain.model.ids.RouteId
import org.slf4j.LoggerFactory
import org.springframework.batch.core.configuration.annotation.StepScope
import org.springframework.batch.infrastructure.item.ItemProcessor
import org.springframework.stereotype.Component

/**
 * Spring Batch ItemProcessor that converts GTFS routes to Route domain models.
 *
 * This processor:
 * 1. Takes RouteInput containing ParsedRoute and feed context
 * 2. Resolves agency onestop ID from GTFS agency ID
 * 3. Uses feed onestop ID to construct route onestop IDs
 * 4. Converts GTFS route types to RouteType enum
 * 5. Returns RouteBatch containing Route domain models
 *
 * Route onestop IDs follow TransitLand format retrieved from the API.
 */
@Component
@StepScope
class RouteProcessor(private val agencyRepository: AgencyRepository) :
  ItemProcessor<RouteInput, RouteBatch> {

  private val logger = LoggerFactory.getLogger(RouteProcessor::class.java)

  override fun process(item: RouteInput): RouteBatch {
    val (parsedRoute, feedOnestopId) = item

    // Resolve agency onestop ID from GTFS agency ID
    val gtfsAgencyId = parsedRoute.agencyId ?: GTFSAgencyId("default-agency")
    val agency =
      agencyRepository.findByFeedIdAndGtfsAgencyId(FeedId(feedOnestopId), gtfsAgencyId)
        ?: throw IllegalStateException(
          "Agency not found for feed=$feedOnestopId, gtfsAgencyId=$gtfsAgencyId"
        )

    // Use feed onestop ID as base for route ID
    // TransitLand format: route IDs from their API
    val routeOnestopId = "r-${feedOnestopId.substringAfter("f-")}-${parsedRoute.routeId.value}"

    val route =
      Route(
        id = RouteId(routeOnestopId),
        agencyId = agency.agencyOnestopId,
        gtfsRouteId = parsedRoute.routeId,
        shortName = parsedRoute.shortName,
        longName = parsedRoute.longName ?: parsedRoute.shortName ?: parsedRoute.routeId.value,
        routeType = RouteType.fromGtfsValue(parsedRoute.type ?: 3),
        color = null,
        textColor = null,
        active = true,
      )

    logger.debug(
      "Processed route: {} ({}) -> {} (agency: {})",
      parsedRoute.shortName ?: parsedRoute.routeId.value,
      parsedRoute.longName,
      routeOnestopId,
      agency.agencyOnestopId.value,
    )

    return RouteBatch(listOf(route))
  }
}
