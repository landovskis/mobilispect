package com.mobilispect.backend.route.domain.model.ids

/**
 * Value class for Route identifiers using Transitland Onestop ID format.
 * Ensures type safety and prevents ID mixups across domain boundaries.
 *
 * Format: r-{geohash}-{route_identifier}
 * Example: r-f25d-100 (Route 100 in Montreal area)
 *
 * The geohash is inherited from the agency's Onestop ID, providing
 * geographic context while maintaining global uniqueness.
 *
 * Per constitutional Code Quality First requirements (FR-018).
 *
 * Now using @JvmInline for zero-overhead type safety in the domain layer.
 * Data layer uses plain String IDs for Hibernate 7 compatibility.
 */
@JvmInline
value class RouteId(val value: String) {
    init {
        require(value.isNotBlank()) { "Route ID cannot be blank" }
        require(value.startsWith("r-") || value.length <= 50) {
            "Route ID must be in Onestop format (r-{geohash}-{identifier}) or legacy format"
        }
    }

    override fun toString(): String = value

    companion object {
        fun from(value: String?): RouteId? =
            value?.takeIf { it.isNotBlank() }?.let { RouteId(it) }
    }
}
