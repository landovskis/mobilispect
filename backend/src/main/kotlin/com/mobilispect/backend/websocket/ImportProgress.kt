package com.mobilispect.backend.websocket

import com.mobilispect.backend.feed.domain.model.ids.FeedId
import com.mobilispect.backend.feed.model.ids.ImportId
import java.time.Instant

/** Real-time import progress data sent to clients via WebSocket */
data class ImportProgress(
  val importId: ImportId,
  val feedId: FeedId,
  val currentStep: String,
  val error: String? = null,
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
