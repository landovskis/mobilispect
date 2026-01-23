package com.mobilispect.backend.feed.api.handler

import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

/**
 * Registry for [FeedDataHandler] instances.
 *
 * Collects handlers via Spring dependency injection and provides query methods for the
 * orchestrator. Handlers are sorted by priority (highest first) on initialization.
 *
 * @param handlers All registered feed data handlers (injected by Spring)
 */
@Component
class FeedDataHandlerRegistry(handlers: List<FeedDataHandler>) {

  private val logger = LoggerFactory.getLogger(FeedDataHandlerRegistry::class.java)

  /** All handlers sorted by priority (highest first). */
  private val allHandlers: List<FeedDataHandler> = handlers.sortedByDescending { it.priority() }

  /** Inverse index: each data type maps to handlers that need it. */
  private val handlersByType: Map<GTFSDataType, List<FeedDataHandler>> =
    GTFSDataType.entries.associateWith { dataType ->
      handlers.filter { dataType in it.dataTypes() }.sortedByDescending { it.priority() }
    }

  init {
    logRegisteredHandlers()
  }

  /** Returns all registered handlers sorted by priority. */
  fun getAllHandlers(): List<FeedDataHandler> = allHandlers

  /** Returns handlers that require the specified data type, sorted by priority. */
  fun getHandlersRequiring(dataType: GTFSDataType): List<FeedDataHandler> {
    return handlersByType[dataType] ?: emptyList()
  }

  /** Returns the set of all data types required by any registered handler. */
  fun getRequiredDataTypes(): Set<GTFSDataType> {
    return allHandlers.flatMap { it.dataTypes() }.toSet()
  }

  /** Groups handlers by their data requirements for bundle sharing optimization. */
  fun groupByDataRequirements(): Map<Set<GTFSDataType>, List<FeedDataHandler>> {
    return allHandlers.groupBy { it.dataTypes() }
  }

  /** Returns the number of registered handlers. */
  fun handlerCount(): Int = allHandlers.size

  /** Logs all registered handlers for debugging. */
  private fun logRegisteredHandlers() {
    if (allHandlers.isEmpty()) {
      logger.info("No feed data handlers registered")
      return
    }

    logger.info("Registered {} feed data handlers:", allHandlers.size)
    allHandlers.forEach { handler ->
      logger.info(
        "  - {}: {} (priority: {})",
        handler::class.simpleName,
        handler.dataTypes().joinToString(),
        handler.priority(),
      )
    }
  }
}
