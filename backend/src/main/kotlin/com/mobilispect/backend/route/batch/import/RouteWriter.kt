package com.mobilispect.backend.route.batch.import

import com.mobilispect.backend.route.domain.repository.RouteRepository
import com.mobilispect.backend.route.events.RouteImported
import org.slf4j.LoggerFactory
import org.springframework.batch.core.annotation.BeforeStep
import org.springframework.batch.core.configuration.annotation.StepScope
import org.springframework.batch.core.step.StepExecution
import org.springframework.batch.infrastructure.item.Chunk
import org.springframework.batch.infrastructure.item.ItemWriter
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

/**
 * Spring Batch ItemWriter that persists routes to the database.
 *
 * This writer:
 * 1. Receives chunks of RouteBatch items from the processor
 * 2. For each route, checks if it already exists in the database
 * 3. Creates new routes or updates existing ones
 * 4. Publishes RouteImported domain events for each route
 * 5. Tracks and logs statistics about routes created/updated
 *
 * The writer is transactional - all routes in a chunk are saved atomically. If any error occurs,
 * the entire chunk is rolled back.
 *
 * Constitutional Requirements:
 * - Event-driven architecture: Publishes domain events for route import
 * - Module boundaries: Uses repository interface, not direct JPA access
 */
@Component
@StepScope
class RouteWriter(
  private val routeRepository: RouteRepository,
  private val eventPublisher: ApplicationEventPublisher,
) : ItemWriter<RouteBatch> {

  private val logger = LoggerFactory.getLogger(RouteWriter::class.java)

  private var stepExecution: StepExecution? = null
  private var cumulativeRoutesProcessed = 0
  private var cumulativeRoutesCreated = 0
  private var cumulativeRoutesUpdated = 0

  @BeforeStep
  fun beforeStep(stepExecution: StepExecution) {
    this.stepExecution = stepExecution
  }

  @Transactional
  override fun write(chunk: Chunk<out RouteBatch>) {
    val batches = chunk.items

    logger.info("Writing {} route batches to database", batches.size)

    var chunkRoutesProcessed = 0
    var chunkRoutesCreated = 0
    var chunkRoutesUpdated = 0

    // Build map of FeedLocalRouteId -> Route for RouteVariantProcessor
    val routesByFeedLocalId =
      mutableMapOf<String, com.mobilispect.backend.route.domain.model.Route>()

    for ((batchIndex, batch) in batches.withIndex()) {
      logger.debug(
        "  Batch {}/{}: Processing {} routes",
        batchIndex + 1,
        batches.size,
        batch.routes.size,
      )

      batch.routes.forEach { route ->
        val existing = routeRepository.findById(route.id)

        val saved =
          if (existing != null) {
            // Update existing route
            chunkRoutesUpdated++
            logger.debug(
              "    Updating route {} ({})",
              route.shortName ?: route.id.value,
              route.id.value,
            )
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
            // Create new route
            chunkRoutesCreated++
            logger.info(
              "    ✓ Creating route {} - {} ({})",
              route.shortName ?: route.id,
              route.longName,
              route.routeType.value,
            )
            routeRepository.save(route)
          }

        // Publish domain event
        eventPublisher.publishEvent(RouteImported(routeId = saved.id))

        chunkRoutesProcessed++
      }

      // Merge this batch's routesByFeedLocalId map into cumulative map
      batch.routesByFeedLocalId.forEach { (feedLocalId, route) ->
        routesByFeedLocalId[feedLocalId.value] = route
      }
    }

    // Update cumulative statistics
    cumulativeRoutesProcessed += chunkRoutesProcessed
    cumulativeRoutesCreated += chunkRoutesCreated
    cumulativeRoutesUpdated += chunkRoutesUpdated

    logger.info(
      """
            ✓ Chunk complete:
              • Routes: {} total ({} created, {} updated)
              • Cumulative: {} total ({} created, {} updated)
            """
        .trimIndent(),
      chunkRoutesProcessed,
      chunkRoutesCreated,
      chunkRoutesUpdated,
      cumulativeRoutesProcessed,
      cumulativeRoutesCreated,
      cumulativeRoutesUpdated,
    )

    // Record metrics in step execution context
    recordMetrics(chunkRoutesProcessed, chunkRoutesCreated, chunkRoutesUpdated)

    // Store route map in job execution context for RouteVariantProcessor
    storeRouteMap(routesByFeedLocalId)
  }

  private fun recordMetrics(processed: Int, created: Int, updated: Int) {
    if (processed == 0) {
      return
    }

    val context = stepExecution?.executionContext
    context?.putInt("routesProcessed", cumulativeRoutesProcessed)
    context?.putInt("routesCreated", cumulativeRoutesCreated)
    context?.putInt("routesUpdated", cumulativeRoutesUpdated)

    stepExecution
      ?.jobExecution
      ?.executionContext
      ?.putInt("routesProcessed", cumulativeRoutesProcessed)
    stepExecution?.jobExecution?.executionContext?.putInt("routesCreated", cumulativeRoutesCreated)
    stepExecution?.jobExecution?.executionContext?.putInt("routesUpdated", cumulativeRoutesUpdated)
  }

  /**
   * Stores the route map in job execution context for use by RouteVariantProcessor.
   *
   * This map allows RouteVariantReader to avoid querying the database and directly access the
   * routes that were just persisted.
   */
  private fun storeRouteMap(
    routesByFeedLocalId: Map<String, com.mobilispect.backend.route.domain.model.Route>
  ) {
    if (routesByFeedLocalId.isEmpty()) {
      return
    }

    logger.info(
      "Storing {} routes in job execution context for variant processing",
      routesByFeedLocalId.size,
    )
    stepExecution?.jobExecution?.executionContext?.put("routesByFeedLocalId", routesByFeedLocalId)
  }
}
