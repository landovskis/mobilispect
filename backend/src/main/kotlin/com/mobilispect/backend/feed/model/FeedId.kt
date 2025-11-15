package com.mobilispect.backend.feed.model

/**
 * Strongly typed identifier for feeds (Transit.land onestop IDs).
 *
 * This inline value class provides compile-time safety when passing feed IDs
 * through the system while still compiling down to a single String instance.
 */
@JvmInline
value class FeedId(val value: String) {
    init {
        require(value.isNotBlank()) { "FeedId cannot be blank" }
    }

    override fun toString(): String = value

    companion object {
        fun from(value: String?): FeedId? = value?.takeIf { it.isNotBlank() }?.let { FeedId(it) }
    }
}
