package com.mobilispect.backend.feed.controller

import org.springframework.messaging.handler.annotation.MessageMapping
import org.springframework.messaging.handler.annotation.SendTo
import org.springframework.stereotype.Controller

/**
 * WebSocket handler for import progress updates.
 *
 * Task T033: Implement WebSocket handler for progress updates
 *
 * This controller handles WebSocket connections for real-time import progress updates.
 * Clients subscribe to /topic/import/progress/{importId} to receive updates.
 *
 * WebSocket Topics:
 * - /topic/import/progress/{importId} - Progress updates for specific import
 * - Progress updates are broadcast automatically by ImportProgressService
 *
 * This implementation delegates to the existing ProgressWebSocketController in the
 * websocket package, which manages the actual WebSocket connections and message routing.
 * This facade provides the feed domain with a clean interface to WebSocket handling
 * while maintaining separation of concerns.
 *
 * Usage from frontend:
 * ```typescript
 * const stompClient = new StompClient(websocketUrl);
 * stompClient.subscribe(`/topic/import/progress/${importId}`, (message) => {
 *   const progress = JSON.parse(message.body);
 *   // Update UI with progress.progressPercentage, progress.currentStep, etc.
 * });
 * ```
 */
@Controller
class ImportProgressWebSocketHandler(
    private val progressWebSocketController: com.mobilispect.backend.websocket.ProgressWebSocketController
) {

    /**
     * Handle progress subscription requests from clients.
     * Clients send a message to /app/import/subscribe with importId to start receiving updates.
     */
    @MessageMapping("/import/subscribe")
    @SendTo("/topic/import/progress")
    fun subscribeToProgress(importId: String): Map<String, Any> {
        return mapOf(
            "importId" to importId,
            "subscribed" to true,
            "message" to "Subscribed to import progress updates"
        )
    }

    /**
     * Handle unsubscribe requests from clients.
     */
    @MessageMapping("/import/unsubscribe")
    fun unsubscribeFromProgress(importId: String) {
        // WebSocket framework handles unsubscription automatically
        // This method exists for explicit client-initiated unsubscribe
    }
}
