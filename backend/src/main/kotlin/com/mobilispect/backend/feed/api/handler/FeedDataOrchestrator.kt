package com.mobilispect.backend.feed.api.handler

import com.mobilispect.backend.feed.api.GTFSData
import com.mobilispect.backend.feed.domain.model.ids.FeedId
import com.mobilispect.backend.feed.model.ids.ImportId
import java.time.Instant
import java.util.concurrent.CompletableFuture
import org.slf4j.LoggerFactory
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Component

/**
 * Orchestrates the distribution of GTFS data to registered handlers.
 *
 * The orchestrator:
 * 1. Groups handlers by their data requirements for bundle sharing
 * 2. Creates selective bundles containing only the data each handler group needs
 * 3. Executes handlers asynchronously in parallel (respecting priority order)
 * 4. Publishes completion or failure events for each handler
 *
 * Memory optimization: Handlers with the same data requirements share bundle instances.
 *
 * @param registry Registry of all registered feed data handlers
 * @param eventPublisher Spring event publisher for handler completion events
 */
@Component
class FeedDataOrchestrator(
  private val registry: FeedDataHandlerRegistry,
  private val eventPublisher: ApplicationEventPublisher,
) {

  private val logger = LoggerFactory.getLogger(FeedDataOrchestrator::class.java)

  /**
   * Distributes GTFS data to all registered handlers.
   *
   * @param feedId The feed being imported
   * @param importId Unique identifier for this import operation
   * @param gtfsData The parsed GTFS data to distribute
   */
  fun distributeGTFSData(feedId: FeedId, importId: ImportId, gtfsData: GTFSData) {
    val context = ImportContext(importId, Instant.now())
    val handlerGroups = registry.groupByDataRequirements()

    logger.info(
      "Distributing GTFS data for feed {} to {} handlers in {} groups",
      feedId.value,
      registry.handlerCount(),
      handlerGroups.size,
    )

    handlerGroups.forEach { (requiredTypes, handlersForGroup) ->
      // Create bundle with only required data (memory optimization)
      val bundle = createSelectiveBundle(feedId, gtfsData, requiredTypes)

      logger.debug(
        "Processing group with {} handlers requiring {}",
        handlersForGroup.size,
        requiredTypes.joinToString(),
      )

      // Execute handlers for this group asynchronously
      handlersForGroup.forEach { handler ->
        executeHandlerAsync(handler, feedId, importId, bundle, context)
      }
    }
  }

  /**
   * Creates a bundle containing only the data types specified.
   *
   * This optimization reduces memory usage by not including data that handlers don't need.
   */
  private fun createSelectiveBundle(
    feedId: FeedId,
    gtfsData: GTFSData,
    requiredTypes: Set<GTFSDataType>,
  ): GTFSDataBundle {
    return GTFSDataBundle(
      feedId = feedId,
      agencies = if (GTFSDataType.AGENCY in requiredTypes) gtfsData.agencies else emptyList(),
      routes = if (GTFSDataType.ROUTE in requiredTypes) gtfsData.routes else emptyList(),
      trips = if (GTFSDataType.TRIP in requiredTypes) gtfsData.trips else emptyList(),
      stops = if (GTFSDataType.STOP in requiredTypes) gtfsData.stops else emptyList(),
      shapes = if (GTFSDataType.SHAPE in requiredTypes) gtfsData.shapes else emptyMap(),
      // Note: stopTimes, frequencies, and calendars would be included here when
      // the GTFS parsing supports them
    )
  }

  /** Executes a handler asynchronously and publishes completion/failure events. */
  private fun executeHandlerAsync(
    handler: FeedDataHandler,
    feedId: FeedId,
    importId: ImportId,
    bundle: GTFSDataBundle,
    context: ImportContext,
  ) {
    CompletableFuture.runAsync {
      val handlerName = handler::class.simpleName ?: "UnknownHandler"
      logger.debug("Executing handler {} for feed {}", handlerName, feedId.value)

      try {
        val result = handler.handle(feedId, bundle, context)

        logger.info(
          "Handler {} completed for feed {}: {} records processed",
          handlerName,
          feedId.value,
          result.processedCount(),
        )

        eventPublisher.publishEvent(
          FeedDataHandlerCompleted(
            feedId = feedId,
            importId = importId,
            dataTypes = handler.dataTypes(),
            result = result,
            handlerClass = handler::class.java,
          )
        )
      } catch (e: Exception) {
        logger.error("Handler {} failed for feed {}", handlerName, feedId.value, e)

        eventPublisher.publishEvent(
          FeedDataHandlerFailed(
            feedId = feedId,
            importId = importId,
            dataTypes = handler.dataTypes(),
            error = ImportError(null, e.message ?: "Unknown error", e),
            handlerClass = handler::class.java,
          )
        )
      }
    }
  }
}
