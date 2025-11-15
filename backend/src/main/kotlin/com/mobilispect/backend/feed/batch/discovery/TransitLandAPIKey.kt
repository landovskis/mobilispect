package com.mobilispect.backend.feed.batch.discovery

/**
 * Type-safe wrapper for Transit.land API keys.
 *
 * This value class provides type safety for API keys, preventing accidental
 * mixing of API keys with other string values and making the API more explicit.
 *
 * Value classes have zero runtime overhead - they are represented as the
 * underlying String type at runtime with no wrapper object allocation.
 *
 * @property value The actual API key string
 */
@JvmInline
value class TransitLandAPIKey(val value: String) {
    init {
        require(value.isNotBlank()) { "Transit.land API key cannot be blank" }
    }

    companion object {
        /**
         * Creates a TransitLandAPIKey from a nullable string.
         * Returns null if the input is null or blank.
         */
        fun fromNullable(apiKey: String?): TransitLandAPIKey? {
            return apiKey?.takeIf { it.isNotBlank() }?.let { TransitLandAPIKey(it) }
        }
    }

    override fun toString(): String = "TransitLandAPIKey(***)"
}