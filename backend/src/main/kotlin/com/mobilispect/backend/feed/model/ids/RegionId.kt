package com.mobilispect.backend.feed.model.ids

/**
 * Value class for Metropolitan Region identifiers using Onestop ID format.
 * Ensures type safety and prevents ID mixups across domain boundaries.
 *
 * Per constitutional Code Quality First requirements (FR-018).
 */
@JvmInline
value class RegionId(val value: String) {
    init {
        require(value.isNotBlank()) { "Region ID cannot be blank" }
    }

    override fun toString(): String = value
}
