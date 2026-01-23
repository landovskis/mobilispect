package com.mobilispect.backend.feed.api.handler

/**
 * Represents an error that occurred during feed data import.
 *
 * @property recordId Optional identifier of the specific record that caused the error
 * @property message Human-readable description of the error
 * @property exception Optional underlying exception that caused the error
 */
data class ImportError(
  val recordId: String? = null,
  val message: String,
  val exception: Throwable? = null,
)
