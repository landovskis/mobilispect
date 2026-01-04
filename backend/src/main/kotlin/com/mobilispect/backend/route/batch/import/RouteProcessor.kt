package com.mobilispect.backend.route.batch.import

import com.mobilispect.backend.agency.domain.model.ids.AgencyId
import com.mobilispect.backend.agency.domain.repository.AgencyRepository
import com.mobilispect.backend.feed.api.ids.FeedLocalAgencyId
import com.mobilispect.backend.feed.api.ids.FeedLocalRouteId
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
  private lateinit var routesByFeedLocalId: MutableMap<FeedLocalRouteId, Route>

  override fun process(item: RouteInput): RouteBatch {
    val (parsedRoute, feedId) = item

    val gtfsAgencyId = parsedRoute.agencyId ?: FeedLocalAgencyId("default-agency")
    val agencyId = AgencyId(FeedId(feedId), gtfsAgencyId)
    val agency =
      agencyRepository.findById(agencyId)
        ?: throw IllegalStateException(
          "Agency not found for feed=$feedId, gtfsAgencyId=$gtfsAgencyId"
        )
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
    routesByFeedLocalId[parsedRoute.routeId] = route

    logger.debug(
      "Processed route: {} ({}) -> {} (agency: {})",
      parsedRoute.shortName ?: parsedRoute.routeId.value,
      parsedRoute.longName,
      route.id,
      agencyId,
    )

    return RouteBatch(listOf(route), routesByFeedLocalId)
  }
}
