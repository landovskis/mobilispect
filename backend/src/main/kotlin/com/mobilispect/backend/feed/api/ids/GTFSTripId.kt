package com.mobilispect.backend.feed.api.ids

/**
 * Type-safe identifier for GTFS trip IDs.
 *
 * Enforces compile-time distinction between GTFS trip IDs and other identifiers.
 * Implemented as an inline value class for zero runtime overhead.
 */
@JvmInline
value class GTFSTripId(val value: String) {
    init {
        require(value.isNotBlank()) { "GTFS Trip ID cannot be blank" }
    }

    override fun toString(): String = value

    companion object {
        fun from(value: String?): GTFSTripId? =
            value?.takeIf { it.isNotBlank() }?.let { GTFSTripId(it) }
    }
}
