package com.mobilispect.backend.route.batch.variant

import com.mobilispect.backend.feed.api.GTFSData
import com.mobilispect.backend.feed.api.GTFSTrip
import com.mobilispect.backend.feed.api.ids.FeedLocalRouteId
import com.mobilispect.backend.route.domain.model.Route
import com.mobilispect.backend.route.domain.model.RouteVariant
import com.mobilispect.backend.route.domain.model.ids.VariantHash
import com.mobilispect.backend.route.domain.repository.RouteRepository
import com.mobilispect.backend.route.domain.repository.RouteVariantRepository
import com.mobilispect.backend.route.events.RouteVariantIdentified
import java.security.MessageDigest
import java.time.Instant
import org.slf4j.LoggerFactory
import org.springframework.batch.core.step.StepExecution
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Service

@Service
class RouteVariantImportService(
  private val routeRepository: RouteRepository,
  private val routeVariantRepository: RouteVariantRepository,
  private val eventPublisher: ApplicationEventPublisher,
) {
  private val logger = LoggerFactory.getLogger(RouteVariantImportService::class.java)

  /**
   * Execute route variant import from Spring Batch step execution context.
   *
   * Delegates to [processVariants] after extracting data from the context.
   */
  fun execute(stepExecution: StepExecution) {
    val parsedData =
      stepExecution.jobExecution.executionContext.get("parsedData") as? GTFSData
        ?: throw IllegalStateException("ParsedGtfsData not found in job execution context")

    @Suppress("UNCHECKED_CAST")
    val routesByFeedLocalId =
      stepExecution.jobExecution.executionContext.get("routesByFeedLocalId") as? Map<String, Route>
        ?: emptyMap()

    val variants = processVariants(parsedData, routesByFeedLocalId)

    // Store results in execution context for downstream steps
    val stepContext = stepExecution.executionContext
    stepContext.putInt("variantsProcessed", variants.size)

    val jobContext = stepExecution.jobExecution.executionContext
    jobContext.putInt("variantsProcessed", variants.size)
  }

  /**
   * Process route variants from parsed GTFS data.
   *
   * This method can be called directly for synchronous processing or from Spring Batch.
   *
   * @param parsedData Parsed GTFS data containing trips and stops
   * @param routesByFeedLocalId Map of feed-local route ID to Route entity (from RouteImportService)
   * @return List of persisted RouteVariant entities
   */
  fun processVariants(
    parsedData: GTFSData,
    routesByFeedLocalId: Map<String, Route> = emptyMap(),
  ): List<RouteVariant> {
    val stopsById = parsedData.stops.associateBy { it.stopId.value }
    val tripsByGtfsRouteId: Map<FeedLocalRouteId, List<GTFSTrip>> =
      parsedData.trips.groupBy { it.routeId }

    val persistedRoutes =
      if (routesByFeedLocalId.isNotEmpty()) {
        routesByFeedLocalId.values.toList()
      } else {
        routeRepository.findAll()
      }

    val routeMap =
      persistedRoutes
        .filter { route -> tripsByGtfsRouteId.containsKey(route.id.feedLocalId()) }
        .associateWith { route -> tripsByGtfsRouteId[route.id.feedLocalId()] ?: emptyList() }

    val allVariants = mutableListOf<RouteVariant>()
    var variantsCreated = 0
    var variantsUpdated = 0

    routeMap.forEach { (route, trips) ->
      if (trips.isEmpty()) {
        return@forEach
      }

      val variants = identifyVariants(route, trips, stopsById)
      variants.forEach { variant ->
        val existing = routeVariantRepository.findById(variant.id)
        val saved =
          if (existing != null) {
            variantsUpdated++
            routeVariantRepository.save(
              existing.copy(lastSeen = variant.lastSeen, headsign = variant.headsign, active = true)
            )
          } else {
            variantsCreated++
            routeVariantRepository.save(variant)
          }

        eventPublisher.publishEvent(
          RouteVariantIdentified(variantId = saved.id, routeId = saved.routeId)
        )
        allVariants.add(saved)
      }

      logger.info(
        "Identified {} variants from {} trips for route {} ({})",
        variants.size,
        trips.size,
        route.shortName ?: route.id,
        route.id.value,
      )
    }

    logger.info(
      "Persisted route variants (total={}, created={}, updated={})",
      allVariants.size,
      variantsCreated,
      variantsUpdated,
    )

    return allVariants
  }

  private fun identifyVariants(
    route: Route,
    trips: List<GTFSTrip>,
    stopsById: Map<String, com.mobilispect.backend.feed.api.GTFSStop>,
  ): List<RouteVariant> {
    val variants = mutableMapOf<String, RouteVariant>()
    var skippedTrips = 0

    trips.forEach { trip ->
      val stopIds = trip.stopTimes.sortedBy { it.stopSequence }.map { it.stopId }
      if (stopIds.size < 2) {
        skippedTrips++
        return@forEach
      }

      val stopNames = stopIds.map { stopId -> stopsById[stopId.value]?.name ?: stopId.value }
      val hash = generateVariantHash(stopIds.map { it.value })

      if (!variants.containsKey(hash.value)) {
        val now = Instant.now()
        val variant =
          RouteVariant(
            id = hash,
            routeId = route.id,
            directionId = trip.directionId,
            headsign = trip.headsign,
            stopPattern = stopIds.joinToString("|") { it.value },
            stopNamePattern = stopNames.joinToString("|"),
            stopCount = stopIds.size,
            firstStopId = stopIds.first().value,
            lastStopId = stopIds.last().value,
            firstSeen = now,
            lastSeen = now,
          )
        variants[hash.value] = variant
      } else {
        val existing = variants[hash.value]!!
        variants[hash.value] = existing.copy(lastSeen = Instant.now())
      }
    }

    if (skippedTrips > 0) {
      logger.debug(
        "Skipped {} trips with fewer than 2 stops for route {}",
        skippedTrips,
        route.id.value,
      )
    }

    return variants.values.toList()
  }

  private fun generateVariantHash(stopIds: List<String>): VariantHash {
    require(stopIds.size >= 2) { "Variant hash requires at least two stops" }
    val concatenated = stopIds.joinToString(separator = "|")
    val digest = MessageDigest.getInstance("SHA-256").digest(concatenated.toByteArray())
    val hex = digest.joinToString("") { "%02x".format(it) }
    return VariantHash(hex)
  }
}
