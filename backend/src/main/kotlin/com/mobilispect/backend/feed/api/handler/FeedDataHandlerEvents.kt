package com.mobilispect.backend.feed.api.handler

import com.mobilispect.backend.feed.domain.model.ids.FeedId
import com.mobilispect.backend.feed.model.ids.ImportId

/**
 * Event published when a feed data handler completes successfully.
 *
 * @property feedId The feed that was processed
 * @property importId Unique identifier for the import operation
 * @property dataTypes The data types that were processed
 * @property result The result of the handler execution
 * @property handlerClass The class of the handler that completed
 */
data class FeedDataHandlerCompleted(
  val feedId: FeedId,
  val importId: ImportId,
  val dataTypes: Set<GTFSDataType>,
  val result: ImportResult,
  val handlerClass: Class<out FeedDataHandler>,
)

/**
 * Event published when a feed data handler fails.
 *
 * @property feedId The feed that was being processed
 * @property importId Unique identifier for the import operation
 * @property dataTypes The data types that were being processed
 * @property error The error that caused the failure
 * @property handlerClass The class of the handler that failed
 */
data class FeedDataHandlerFailed(
  val feedId: FeedId,
  val importId: ImportId,
  val dataTypes: Set<GTFSDataType>,
  val error: ImportError,
  val handlerClass: Class<out FeedDataHandler>,
)
