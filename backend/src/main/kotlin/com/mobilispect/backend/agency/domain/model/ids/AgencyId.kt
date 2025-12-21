package com.mobilispect.backend.agency.domain.model.ids

import jakarta.persistence.Embeddable
import java.io.Serializable

/**
 * Value class for Agency identifiers using Onestop ID format.
 * Ensures type safety and prevents ID mixups across domain boundaries.
 *
 * Format: o-geohash-name (e.g., o-9q8y-sfmta)
 *
 * Per constitutional Code Quality First requirements (FR-018).
 *
 * Note: Not using @JvmInline due to Hibernate 7 incompatibility with AttributeConverter on @Id fields.
 * Using @Embeddable for proper Hibernate 7 mapping.
 */
@Embeddable
data class AgencyId(val value: String = "") : Serializable {
    init {
        if (value.isNotBlank()) {
            require(value.isNotBlank()) { "Agency ID cannot be blank" }
        }
    }

    override fun toString(): String = value

    companion object {
        fun from(value: String?): AgencyId? = value?.takeIf { it.isNotBlank() }?.let { AgencyId(it) }
    }
}
