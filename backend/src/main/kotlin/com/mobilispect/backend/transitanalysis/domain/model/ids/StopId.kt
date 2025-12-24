package com.mobilispect.backend.transitanalysis.domain.model.ids

/**
 * Value class for Stop identifiers using Transitland Onestop ID format.
 * Ensures type safety and prevents ID mixups across domain boundaries.
 *
 * Format: s-geohash-name (e.g., s-9q8y-market~st)
 *
 * Per constitutional Code Quality First requirements (FR-018).
 *
 * Using @JvmInline for zero-overhead type safety in the domain layer.
 * Data layer uses plain String IDs for Hibernate 7 compatibility.
 */
@JvmInline
value class StopId(val value: String) {
    init {
        require(value.isNotBlank()) { "Stop ID cannot be blank" }
    }

    override fun toString(): String = value

    companion object {
        fun from(value: String?): StopId? =
            value?.takeIf { it.isNotBlank() }?.let { StopId(it) }
    }
}
