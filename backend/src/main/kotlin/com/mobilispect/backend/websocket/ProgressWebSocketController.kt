package com.mobilispect.backend.websocket

import com.mobilispect.backend.feed.model.ids.ImportId
import com.mobilispect.backend.feed.service.FeedImportProgressService
import org.slf4j.LoggerFactory
import org.springframework.messaging.handler.annotation.DestinationVariable
import org.springframework.messaging.handler.annotation.MessageMapping
import org.springframework.messaging.handler.annotation.SendTo
import org.springframework.messaging.simp.annotation.SubscribeMapping
import org.springframework.stereotype.Controller

/** WebSocket controller for handling import progress subscriptions and requests */
@Controller
class ProgressWebSocketController(private val progressService: FeedImportProgressService) {
  private val logger = LoggerFactory.getLogger(ProgressWebSocketController::class.java)

  /** Handle requests for current progress state of a specific import */
  @MessageMapping("/import/progress/{importId}/request")
  @SendTo("/topic/import/progress/{importId}")
  fun requestProgress(@DestinationVariable importId: String): ProgressUpdate {
    logger.debug("Progress request received for import: {}", importId)

    val progress = progressService.getProgress(ImportId.fromString(importId))
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

    val activeImportIds = progressService.getActiveImportIds().map { it.toString() }
    return ActiveImportsResponse(activeImports = activeImportIds)
  }

  /**
   * Handle initial subscription to progress topic Sends current state immediately upon subscription
   */
  @SubscribeMapping("/topic/import/progress/{importId}")
  fun onSubscribe(@DestinationVariable importId: String): ProgressUpdate {
    logger.debug("Client subscribed to progress for import: {}", importId)

    val progress = progressService.getProgress(ImportId.fromString(importId))
    return if (progress != null) {
      ProgressUpdate(progress = progress)
    } else {
      ProgressUpdate(error = "Import not found or not active")
    }
  }
}
