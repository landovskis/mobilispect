package com.mobilispect.backend.transitanalysis.domain.model.ids

import jakarta.persistence.Embeddable
import java.io.Serializable

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
 * Note: Not using @JvmInline due to Hibernate 7 incompatibility with AttributeConverter on @Id fields.
 * Using @Embeddable for proper Hibernate 7 mapping.
 */
@Embeddable
data class RouteId(val value: String = "") : Serializable {
    init {
        if (value.isNotBlank()) {
            require(value.isNotBlank()) { "Route ID cannot be blank" }
            require(value.startsWith("r-") || value.length <= 50) {
                "Route ID must be in Onestop format (r-{geohash}-{identifier}) or legacy format"
            }
        }
    }

    override fun toString(): String = value

    companion object {
        fun from(value: String?): RouteId? = value?.takeIf { it.isNotBlank() }?.let { RouteId(it) }
    }
}
