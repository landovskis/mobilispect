package com.mobilispect.backend.route.batch.import

import com.mobilispect.backend.agency.domain.model.ids.AgencyId
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
 * 2. Uses feed onestop ID to construct route onestop IDs
 * 3. Converts GTFS route types to RouteType enum
 * 4. Returns RouteBatch containing Route domain models
 *
 * Route onestop IDs follow TransitLand format retrieved from the API.
 */
@Component
@StepScope
class RouteProcessor : ItemProcessor<RouteInput, RouteBatch> {

    private val logger = LoggerFactory.getLogger(RouteProcessor::class.java)

    override fun process(item: RouteInput): RouteBatch {
        val (parsedRoute, feedOnestopId) = item

        // Use feed onestop ID as base for route ID
        // TransitLand format: route IDs from their API
        val routeOnestopId = "r-${feedOnestopId.substringAfter("f-")}-${parsedRoute.routeId}"

        val route = Route(
            id = RouteId(routeOnestopId),
            agencyId = AgencyId(parsedRoute.agencyId ?: "default-agency"),
            gtfsRouteId = parsedRoute.routeId,
            shortName = parsedRoute.shortName,
            longName = parsedRoute.longName ?: parsedRoute.shortName ?: parsedRoute.routeId,
            routeType = RouteType.fromGtfsValue(parsedRoute.type ?: 3),
            color = null,
            textColor = null,
            active = true
        )

        logger.debug(
            "Processed route: {} ({}) -> {}",
            parsedRoute.shortName ?: parsedRoute.routeId,
            parsedRoute.longName,
            routeOnestopId
        )

        return RouteBatch(listOf(route))
    }
}
