package com.mobilispect.backend.route.batch.variant

import com.mobilispect.backend.feed.gtfs.ParsedGtfsData
import com.mobilispect.backend.feed.gtfs.ParsedTrip
import com.mobilispect.backend.route.domain.model.Route
import com.mobilispect.backend.route.domain.repository.RouteRepository
import org.slf4j.LoggerFactory
import org.springframework.batch.core.step.StepExecution
import org.springframework.batch.core.annotation.BeforeStep
import org.springframework.batch.core.configuration.annotation.StepScope
import org.springframework.batch.infrastructure.item.ItemReader
import org.springframework.stereotype.Component

/**
 * Spring Batch ItemReader that reads persisted routes and produces RouteVariantInput items.
 *
 * This reader:
 * 1. Retrieves ParsedGtfsData from the job execution context
 * 2. Fetches persisted Route entities from the database
 * 3. Matches routes to their trips using gtfsRouteId
 * 4. For each route with trips, creates a RouteVariantInput combining:
 *    - Persisted Route entity
 *    - Trips belonging to that route
 *    - Stop metadata map
 * 5. Returns one RouteVariantInput per route for processing
 *
 * The reader processes routes sequentially, yielding one RouteVariantInput at a time.
 * This allows the batch framework to chunk the processing and apply transaction boundaries.
 */
@Component
@StepScope
class RouteVariantReader(
    private val routeRepository: RouteRepository
) : ItemReader<RouteVariantInput> {

    private val logger = LoggerFactory.getLogger(RouteVariantReader::class.java)

    private var parsedData: ParsedGtfsData? = null
    private var routeIterator: Iterator<Map.Entry<Route, List<ParsedTrip>>>? = null
    private var stopsById: Map<String, com.mobilispect.backend.feed.gtfs.ParsedStop> = emptyMap()

    @BeforeStep
    fun beforeStep(stepExecution: StepExecution) {
        // Retrieve parsed data from job execution context
        parsedData = stepExecution.jobExecution.executionContext.get("parsedData") as? ParsedGtfsData
            ?: throw IllegalStateException("ParsedGtfsData not found in job execution context")

        val data = parsedData!!

        logger.info(
            "Initializing RouteVariantReader with {} trips, {} stops from GTFS data",
            data.trips.size,
            data.stops.size
        )

        // Create stop lookup map
        stopsById = data.stops.associateBy { it.stopId }

        // Group trips by GTFS route ID
        val tripsByGtfsRouteId = data.trips.groupBy { it.routeId }

        // Fetch all persisted routes from database
        val persistedRoutes = routeRepository.findAll()
        logger.info("Fetched {} persisted routes from database", persistedRoutes.size)

        // Create lookup map: gtfsRouteId -> Route
        val routesByGtfsId = persistedRoutes.associateBy { it.gtfsRouteId }

        // Match persisted routes to their trips
        val routeMap = persistedRoutes
            .filter { route -> tripsByGtfsRouteId.containsKey(route.gtfsRouteId) }
            .associateWith { route -> tripsByGtfsRouteId[route.gtfsRouteId] ?: emptyList() }

        routeIterator = routeMap.entries.iterator()

        logger.info(
            "Prepared {} routes for variant identification ({} routes had no trips)",
            routeMap.size,
            persistedRoutes.size - routeMap.size
        )
    }

    override fun read(): RouteVariantInput? {
        if (routeIterator == null || !routeIterator!!.hasNext()) {
            return null
        }

        val (route, trips) = routeIterator!!.next()

        return RouteVariantInput(
            route = route,
            trips = trips,
            stopsById = stopsById
        )
    }
}
