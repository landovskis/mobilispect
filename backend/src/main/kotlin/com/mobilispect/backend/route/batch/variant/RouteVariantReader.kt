package com.mobilispect.backend.route.batch.variant

import com.mobilispect.backend.feed.api.GTFSData
import com.mobilispect.backend.feed.api.GTFSStop
import com.mobilispect.backend.feed.api.GTFSTrip
import com.mobilispect.backend.feed.api.ids.FeedLocalRouteId
import com.mobilispect.backend.route.domain.model.Route
import com.mobilispect.backend.route.domain.repository.RouteRepository
import org.slf4j.LoggerFactory
import org.springframework.batch.core.annotation.BeforeStep
import org.springframework.batch.core.configuration.annotation.StepScope
import org.springframework.batch.core.step.StepExecution
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
 *     - Persisted Route entity
 *     - Trips belonging to that route
 *     - Stop metadata map
 * 5. Returns one RouteVariantInput per route for processing
 *
 * The reader processes routes sequentially, yielding one RouteVariantInput at a time. This allows
 * the batch framework to chunk the processing and apply transaction boundaries.
 */
@Component
@StepScope
class RouteVariantReader(private val routeRepository: RouteRepository) :
  ItemReader<RouteVariantInput> {

  private val logger = LoggerFactory.getLogger(RouteVariantReader::class.java)

  private var parsedData: GTFSData? = null
  private var routeIterator: Iterator<Map.Entry<Route, List<GTFSTrip>>>? = null
  private var stopsById: Map<String, GTFSStop> = emptyMap()
  private var routesByFeedLocalId: Map<String, Route> = emptyMap()

  @BeforeStep
  fun beforeStep(stepExecution: StepExecution) {
    // Retrieve parsed data from job execution context
    parsedData =
      stepExecution.jobExecution.executionContext.get("parsedData") as? GTFSData
        ?: throw IllegalStateException("ParsedGtfsData not found in job execution context")

    // Retrieve route map from job execution context (populated by RouteWriter)
    @Suppress("UNCHECKED_CAST")
    routesByFeedLocalId =
      stepExecution.jobExecution.executionContext.get("routesByFeedLocalId") as? Map<String, Route>
        ?: emptyMap()

    if (routesByFeedLocalId.isNotEmpty()) {
      logger.info(
        "Retrieved {} routes from job execution context (avoiding database query)",
        routesByFeedLocalId.size,
      )
    } else {
      logger.warn("Route map not found in job execution context, will fetch from database")
    }

    val data = parsedData!!

    logger.info(
      "Initializing RouteVariantReader with {} trips, {} stops from GTFS data",
      data.trips.size,
      data.stops.size,
    )

    // Create stop lookup map
    stopsById = data.stops.associateBy { it.stopId.value }

    // Group trips by GTFS route ID
    val tripsByGtfsRouteId: Map<FeedLocalRouteId, List<GTFSTrip>> = data.trips.groupBy { it.routeId }

    // Use routes from context if available, otherwise fetch from database
    val persistedRoutes =
      if (routesByFeedLocalId.isNotEmpty()) {
        routesByFeedLocalId.values.toList()
      } else {
        val dbRoutes = routeRepository.findAll()
        logger.info("Fetched {} persisted routes from database", dbRoutes.size)
        dbRoutes
      }

    // Match persisted routes to their trips using GTFS route ID from trip
    val routeMap =
      persistedRoutes
        .filter { route ->
          tripsByGtfsRouteId.containsKey(route.id.feedLocalId())
        }
        .associateWith { route ->
          tripsByGtfsRouteId[route.id.feedLocalId()] ?: emptyList()
        }

    routeIterator = routeMap.entries.iterator()

    logger.info(
      "Prepared {} routes for variant identification ({} routes had no trips)",
      routeMap.size,
      persistedRoutes.size - routeMap.size,
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
      stopsById = stopsById,
      routesByFeedLocalId = routesByFeedLocalId,
    )
  }
}
