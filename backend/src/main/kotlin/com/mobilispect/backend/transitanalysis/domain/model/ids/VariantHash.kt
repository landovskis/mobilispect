package com.mobilispect.backend.transitanalysis.domain.model.ids

import jakarta.persistence.Embeddable
import java.io.Serializable

/**
 * Value class for RouteVariant identifiers using SHA-256 hash.
 * Ensures type safety and prevents ID mixups across domain boundaries.
 *
 * The hash is computed from the ordered stop pattern to uniquely identify
 * each service pattern variant.
 *
 * Format: 64-character hexadecimal string (SHA-256 output)
 *
 * Per constitutional Code Quality First requirements (FR-018).
 *
 * Note: Not using @JvmInline due to Hibernate 7 incompatibility with AttributeConverter on @Id fields.
 * Using @Embeddable for proper Hibernate 7 mapping.
 */
@Embeddable
data class VariantHash(val value: String = "0".repeat(64)) : Serializable {
    init {
        if (value.isNotBlank() && value != "0".repeat(64)) {
            require(value.isNotBlank()) { "Variant hash cannot be blank" }
            require(value.length == 64) { "Variant hash must be 64 characters (SHA-256)" }
            require(value.matches(Regex("^[a-fA-F0-9]{64}$"))) {
                "Variant hash must be a valid hex string"
            }
        }
    }

    override fun toString(): String = value

    companion object {
        fun from(value: String?): VariantHash? =
            value?.takeIf { it.isNotBlank() }?.let { VariantHash(it) }
    }
}
