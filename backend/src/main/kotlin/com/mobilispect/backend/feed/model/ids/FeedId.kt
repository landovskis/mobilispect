package com.mobilispect.backend.feed.model.ids

import jakarta.persistence.Embeddable
import java.io.Serializable

/**
 * Value class for Feed identifiers using Onestop ID format.
 * Ensures type safety and prevents ID mixups across domain boundaries.
 *
 * Per constitutional Code Quality First requirements (FR-018).
 *
 * Note: Not using @JvmInline due to Hibernate 7 incompatibility with AttributeConverter on @Id fields.
 * Using @Embeddable for proper Hibernate 7 mapping.
 */
@Embeddable
data class FeedId(val value: String = "") : Serializable {
    init {
        if (value.isNotBlank()) {
            require(value.isNotBlank()) { "Feed ID cannot be blank" }
        }
    }

    override fun toString(): String = value

    companion object {
        fun from(value: String?): FeedId? = value?.takeIf { it.isNotBlank() }?.let { FeedId(it) }
    }
}
