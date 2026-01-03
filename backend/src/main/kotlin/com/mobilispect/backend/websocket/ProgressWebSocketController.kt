package com.mobilispect.backend.websocket

import org.slf4j.LoggerFactory
import org.springframework.messaging.handler.annotation.DestinationVariable
import org.springframework.messaging.handler.annotation.MessageMapping
import org.springframework.messaging.handler.annotation.SendTo
import org.springframework.messaging.simp.annotation.SubscribeMapping
import org.springframework.stereotype.Controller

/** WebSocket controller for handling import progress subscriptions and requests */
@Controller
class ProgressWebSocketController(
  private val progressEventListener: FeedImportProgressEventListener
) {
  private val logger = LoggerFactory.getLogger(ProgressWebSocketController::class.java)

  /** Handle requests for current progress state of a specific import */
  @MessageMapping("/import/progress/{importId}/request")
  @SendTo("/topic/import/progress/{importId}")
  fun requestProgress(@DestinationVariable importId: String): ProgressUpdate {
    logger.debug("Progress request received for import: {}", importId)

    val progress = progressEventListener.getProgress(importId)
    return if (progress != null) {
      ProgressUpdate(progress = progress)
    } else {
      // Import not active or not found
      ProgressUpdate(error = "Import not found or not active")
    }
  }

  /** Handle requests for list of active imports */
  @MessageMapping("/import/progress/active")
  @SendTo("/topic/import/progress/active")
  fun getActiveImports(): ActiveImportsResponse {
    logger.debug("Active imports request received")

    val activeImportIds = progressEventListener.getActiveImportIds()
    return ActiveImportsResponse(activeImports = activeImportIds)
  }

  /**
   * Handle initial subscription to progress topic Sends current state immediately upon subscription
   */
  @SubscribeMapping("/topic/import/progress/{importId}")
  fun onSubscribe(@DestinationVariable importId: String): ProgressUpdate {
    logger.debug("Client subscribed to progress for import: {}", importId)

    val progress = progressEventListener.getProgress(importId)
    return if (progress != null) {
      ProgressUpdate(progress = progress)
    } else {
      ProgressUpdate(error = "Import not found or not active")
    }
  }
}
