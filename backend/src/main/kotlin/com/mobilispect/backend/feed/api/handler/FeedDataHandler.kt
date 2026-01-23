package com.mobilispect.backend.feed.api.handler

import com.mobilispect.backend.feed.domain.model.ids.FeedId

/**
 * Interface for handlers that process GTFS data during feed imports.
 *
 * Handlers declare which [GTFSDataType]s they need via [dataTypes]. The feed orchestrator invokes
 * each handler with a [GTFSDataBundle] containing all requested data types. This allows handlers
 * that need multiple related data types (e.g., routes + trips + shapes) to receive all data in a
 * single invocation without coordination.
 *
 * Handlers are discovered via Spring component scanning and registered automatically. Use
 * [priority] to control execution order when dependencies exist between handlers.
 *
 * Example single-type handler:
 * ```kotlin
 * @Component
 * class AgencyDataHandler(private val agencyService: AgencyCommandService) : FeedDataHandler {
 *     override fun dataTypes() = setOf(GTFSDataType.AGENCY)
 *
 *     override fun handle(feedId: FeedId, data: GTFSDataBundle, context: ImportContext): ImportResult {
 *         val agencies = data.agencies.map { it.toDomainModel(feedId) }
 *         agencyService.importAgencies(agencies)
 *         return ImportResult.Success(agencies.size)
 *     }
 * }
 * ```
 *
 * Example multi-type handler:
 * ```kotlin
 * @Component
 * class RouteVariantDataHandler(private val routeService: RouteCommandService) : FeedDataHandler {
 *     override fun dataTypes() = setOf(GTFSDataType.ROUTE, GTFSDataType.TRIP, GTFSDataType.SHAPE)
 *     override fun priority() = 5  // After agency processing
 *
 *     override fun handle(feedId: FeedId, data: GTFSDataBundle, context: ImportContext): ImportResult {
 *         // All required data available in single call
 *         val variants = data.routes.map { route ->
 *             val routeTrips = data.trips.filter { it.routeId == route.routeId }
 *             val shapes = routeTrips.mapNotNull { trip ->
 *                 trip.shapeId?.let { shapeId -> data.shapes[shapeId] }
 *             }
 *             RouteVariant.fromGTFS(route, routeTrips, shapes)
 *         }
 *         routeService.importRouteVariants(variants)
 *         return ImportResult.Success(variants.size)
 *     }
 * }
 * ```
 */
interface FeedDataHandler {
  /**
   * Returns the set of GTFS data types this handler needs.
   *
   * The handler will receive a [GTFSDataBundle] containing data for all requested types. Handlers
   * can request any combination of types:
   * - Single type: `setOf(GTFSDataType.AGENCY)`
   * - Multiple types: `setOf(GTFSDataType.ROUTE, GTFSDataType.TRIP, GTFSDataType.SHAPE)`
   *
   * The orchestrator may optimize memory by sharing bundle instances among handlers with the same
   * data requirements.
   */
  fun dataTypes(): Set<GTFSDataType>

  /**
   * Returns the priority for this handler.
   *
   * Higher priority handlers execute first. Default is 0. Use this when handlers have dependencies
   * (e.g., agencies must be processed before routes).
   *
   * Example priorities:
   * - Agency handler: 10 (highest, processed first)
   * - Route handler: 5
   * - Route variant handler: 5 (can run parallel with routes)
   * - Stop spacing handler: 0 (default)
   */
  fun priority(): Int = 0

  /**
   * Process the GTFS data for this feed.
   *
   * @param feedId The feed being imported
   * @param data Bundle containing all requested data types
   * @param context Import context with metadata for logging and correlation
   * @return Result indicating success, failure, or partial success
   */
  fun handle(feedId: FeedId, data: GTFSDataBundle, context: ImportContext): ImportResult
}
