package com.mobilispect.backend.route.batch.variant

import com.mobilispect.backend.agency.domain.model.ids.AgencyId
import com.mobilispect.backend.feed.gtfs.ParsedGtfsData
import com.mobilispect.backend.feed.gtfs.ParsedRoute
import com.mobilispect.backend.feed.gtfs.ParsedTrip
import com.mobilispect.backend.route.domain.model.Route
import com.mobilispect.backend.route.domain.model.RouteType
import com.mobilispect.backend.route.domain.model.ids.RouteId
import org.slf4j.LoggerFactory
import org.springframework.batch.core.step.StepExecution
import org.springframework.batch.core.annotation.BeforeStep
import org.springframework.batch.core.configuration.annotation.StepScope
import org.springframework.batch.infrastructure.item.ItemReader
import org.springframework.stereotype.Component
import java.security.MessageDigest

/**
 * Spring Batch ItemReader that reads parsed GTFS data and produces RouteVariantInput items.
 *
 * This reader:
 * 1. Retrieves ParsedGtfsData from the step execution context
 * 2. Groups trips by route ID
 * 3. For each route, creates a RouteVariantInput combining:
 *    - Route domain model (converted from ParsedRoute)
 *    - Trips belonging to that route
 *    - Stop metadata map
 * 4. Returns one RouteVariantInput per route for processing
 *
 * The reader processes routes sequentially, yielding one RouteVariantInput at a time.
 * This allows the batch framework to chunk the processing and apply transaction boundaries.
 */
@Component
@StepScope
class RouteVariantReader : ItemReader<RouteVariantInput> {

    private val logger = LoggerFactory.getLogger(RouteVariantReader::class.java)

    private var parsedData: ParsedGtfsData? = null
    private var routeIterator: Iterator<Map.Entry<ParsedRoute, List<ParsedTrip>>>? = null
    private var stopsById: Map<String, com.mobilispect.backend.feed.gtfs.ParsedStop> = emptyMap()

    @BeforeStep
    fun beforeStep(stepExecution: StepExecution) {
        // Retrieve parsed data from job execution context
        parsedData = stepExecution.jobExecution.executionContext.get("parsedData") as? ParsedGtfsData
            ?: throw IllegalStateException("ParsedGtfsData not found in job execution context")

        val data = parsedData!!

        logger.info(
            "Initializing RouteVariantReader with {} routes, {} trips, {} stops",
            data.routes.size,
            data.trips.size,
            data.stops.size
        )

        // Create stop lookup map
        stopsById = data.stops.associateBy { it.stopId }

        // Group trips by route ID
        val tripsByRouteId = data.trips.groupBy { it.routeId }

        // Create map of ParsedRoute to its trips
        val routeMap = data.routes
            .filter { route -> tripsByRouteId.containsKey(route.routeId) }
            .associateWith { route -> tripsByRouteId[route.routeId] ?: emptyList() }

        routeIterator = routeMap.entries.iterator()

        logger.info(
            "Prepared {} routes for variant identification ({} routes had no trips)",
            routeMap.size,
            data.routes.size - routeMap.size
        )
    }

    override fun read(): RouteVariantInput? {
        if (routeIterator == null || !routeIterator!!.hasNext()) {
            return null
        }

        val (parsedRoute, trips) = routeIterator!!.next()

        // Convert ParsedRoute to Route domain model
        val route = parsedRouteToRoute(parsedRoute)

        return RouteVariantInput(
            route = route,
            trips = trips,
            stopsById = stopsById
        )
    }

    /**
     * Converts ParsedRoute (GTFS data) to Route domain model.
     *
     * Generates a deterministic onestop ID based on the route's GTFS data.
     */
    private fun parsedRouteToRoute(parsedRoute: ParsedRoute): Route {
        // Generate onestop ID: r-{geohash}-{route_identifier}
        // For now, use a simplified approach with hash of route ID
        val routeOnestopId = generateRouteOnestopId(parsedRoute)

        // Get agency ID or use default
        val agencyId = parsedRoute.agencyId ?: "default-agency"

        return Route(
            id = RouteId(routeOnestopId),
            agencyId = AgencyId(agencyId),
            gtfsRouteId = parsedRoute.routeId,
            shortName = parsedRoute.shortName,
            longName = parsedRoute.longName ?: parsedRoute.shortName ?: parsedRoute.routeId,
            routeType = RouteType.fromGtfsValue(parsedRoute.type ?: 3), // Default to bus if not specified
            color = null,
            textColor = null,
            active = true
        )
    }

    /**
     * Generate a deterministic onestop ID for a route.
     *
     * Format: r-{hash8}-{routeId}
     * where hash8 is first 8 characters of SHA-256 hash of route data
     */
    private fun generateRouteOnestopId(parsedRoute: ParsedRoute): String {
        val composite = "${parsedRoute.agencyId ?: ""}|${parsedRoute.routeId}"
        val digest = MessageDigest.getInstance("SHA-256").digest(composite.toByteArray())
        val hash8 = digest.joinToString("") { "%02x".format(it) }.take(8)

        // Sanitize route ID for onestop format (lowercase, alphanumeric, hyphens)
        val sanitizedRouteId = parsedRoute.routeId
            .lowercase()
            .replace(Regex("[^a-z0-9]+"), "-")
            .trim('-')
            .take(32) // Limit length

        return "r-$hash8-$sanitizedRouteId"
    }
}
