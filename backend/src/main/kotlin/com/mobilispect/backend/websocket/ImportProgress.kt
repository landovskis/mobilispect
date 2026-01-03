package com.mobilispect.backend.websocket

import java.time.Instant

/** Real-time import progress data sent to clients via WebSocket */
data class ImportProgress(
  val importId: String,
  val feedOnestopId: String,
  val progressPercentage: Int,
  val currentStep: String,
  val currentStepNumber: Int,
  val totalSteps: Int,
  val startedAt: Instant,
  val lastUpdatedAt: Instant,
  val estimatedTimeRemainingSeconds: Long? = null,
  val processingRate: Double? = null,
)

/** Progress update message wrapper */
data class ProgressUpdate(
  val progress: ImportProgress? = null,
  val completed: Boolean = false,
  val error: String? = null,
  val finishedAt: Instant? = null,
  val durationSeconds: Long? = null,
)

/** Active imports response */
data class ActiveImportsResponse(val activeImports: List<String>, val error: String? = null)

/** Progress request message */
data class ProgressRequest(val importId: String)
