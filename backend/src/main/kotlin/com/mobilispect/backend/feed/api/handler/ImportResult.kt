package com.mobilispect.backend.feed.api.handler

/**
 * Result of a feed data handler operation.
 *
 * Handlers return this sealed class to indicate the outcome of processing. Three variants are
 * supported:
 * - [Success]: All records processed successfully
 * - [Failure]: Processing failed completely with a single error
 * - [PartialSuccess]: Some records processed, but errors occurred for others
 */
sealed class ImportResult {
  /**
   * All records were processed successfully.
   *
   * @property recordsProcessed The number of records that were processed
   */
  data class Success(val recordsProcessed: Int) : ImportResult()

  /**
   * Processing failed completely.
   *
   * @property error The error that caused the failure
   */
  data class Failure(val error: ImportError) : ImportResult()

  /**
   * Some records were processed, but errors occurred for others.
   *
   * @property recordsProcessed The number of records successfully processed
   * @property errors The list of errors that occurred during processing
   */
  data class PartialSuccess(val recordsProcessed: Int, val errors: List<ImportError>) :
    ImportResult()

  /** Returns true if this result represents a complete success with no errors. */
  fun isSuccess(): Boolean = this is Success

  /** Returns true if this result represents a complete failure. */
  fun isFailure(): Boolean = this is Failure

  /** Returns true if this result has at least one successfully processed record. */
  fun hasProcessedRecords(): Boolean =
    when (this) {
      is Success -> recordsProcessed > 0
      is PartialSuccess -> recordsProcessed > 0
      is Failure -> false
    }

  /** Returns the number of records processed, or 0 if failed. */
  fun processedCount(): Int =
    when (this) {
      is Success -> recordsProcessed
      is PartialSuccess -> recordsProcessed
      is Failure -> 0
    }

  /** Returns all errors, or an empty list if successful. */
  fun errors(): List<ImportError> =
    when (this) {
      is Success -> emptyList()
      is PartialSuccess -> errors
      is Failure -> listOf(error)
    }
}
