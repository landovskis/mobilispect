package com.mobilispect.backend.feed.api.ids

/**
 * Type-safe identifier for GTFS stop IDs.
 *
 * Enforces compile-time distinction between GTFS stop IDs and other identifiers. Implemented as an
 * inline value class for zero runtime overhead.
 */
@JvmInline
value class GTFSStopId(val value: String) {
  init {
    require(value.isNotBlank()) { "GTFS Stop ID cannot be blank" }
  }

  override fun toString(): String = value

  companion object {
    fun from(value: String?): GTFSStopId? =
      value?.takeIf { it.isNotBlank() }?.let { GTFSStopId(it) }
  }
}
