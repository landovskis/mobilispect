package com.mobilispect.backend.route.domain.model.ids

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
 * Now using @JvmInline for zero-overhead type safety in the domain layer.
 * Data layer uses plain String IDs for Hibernate 7 compatibility.
 */
@JvmInline
value class VariantHash(val value: String) {
    init {
        require(value.isNotBlank()) { "Variant hash cannot be blank" }
        require(value.length == 64) { "Variant hash must be 64 characters (SHA-256)" }
        require(value.matches(Regex("^[a-fA-F0-9]{64}$"))) {
            "Variant hash must be a valid hex string"
        }
    }

    override fun toString(): String = value

    companion object {
        fun from(value: String?): VariantHash? =
            value?.takeIf { it.isNotBlank() && it.length == 64 && it.matches(Regex("^[a-fA-F0-9]{64}$")) }
                ?.let { VariantHash(it) }
    }
}
