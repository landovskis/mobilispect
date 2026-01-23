package com.mobilispect.backend.feed.api.handler

import com.mobilispect.backend.feed.model.ids.ImportId
import java.time.Instant

/**
 * Context information for a feed data import operation.
 *
 * Provides metadata about the current import that handlers can use for logging, correlation, and
 * tracing.
 *
 * @property importId Unique identifier for this import operation
 * @property startedAt Timestamp when the import started
 */
data class ImportContext(val importId: ImportId, val startedAt: Instant)
