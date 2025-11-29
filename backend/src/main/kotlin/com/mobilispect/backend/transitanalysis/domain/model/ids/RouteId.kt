package com.mobilispect.backend.transitanalysis.domain.model.ids

/**
 * Value class for Route identifiers.
 * Ensures type safety and prevents ID mixups across domain boundaries.
 *
 * Typically a composite of agency ID and route identifier from GTFS.
 *
 * Per constitutional Code Quality First requirements (FR-018).
 */
@JvmInline
value class RouteId(val value: String) {
    init {
        require(value.isNotBlank()) { "Route ID cannot be blank" }
    }

    override fun toString(): String = value

    companion object {
        fun from(value: String?): RouteId? = value?.takeIf { it.isNotBlank() }?.let { RouteId(it) }
    }
}
