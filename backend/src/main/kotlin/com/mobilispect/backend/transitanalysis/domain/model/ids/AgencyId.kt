package com.mobilispect.backend.transitanalysis.domain.model.ids

/**
 * Value class for Agency identifiers using Onestop ID format.
 * Ensures type safety and prevents ID mixups across domain boundaries.
 *
 * Format: o-geohash-name (e.g., o-9q8y-sfmta)
 *
 * Per constitutional Code Quality First requirements (FR-018).
 */
@JvmInline
value class AgencyId(val value: String) {
    init {
        require(value.isNotBlank()) { "Agency ID cannot be blank" }
    }

    override fun toString(): String = value

    companion object {
        fun from(value: String?): AgencyId? = value?.takeIf { it.isNotBlank() }?.let { AgencyId(it) }
    }
}
