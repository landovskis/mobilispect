package com.mobilispect.backend.feed.model.ids

/**
 * Value class for Feed identifiers using Onestop ID format. Ensures type safety and prevents ID
 * mixups across domain boundaries.
 *
 * Per constitutional Code Quality First requirements (FR-018).
 *
 * Now using @JvmInline for zero-overhead type safety in the domain layer. Data layer uses plain
 * String IDs for Hibernate 7 compatibility.
 */
@JvmInline
value class FeedId(val value: String) {
  init {
    require(value.isNotBlank()) { "Feed ID cannot be blank" }
  }

  override fun toString(): String = value

  companion object {
    fun from(value: String?): FeedId? = value?.takeIf { it.isNotBlank() }?.let { FeedId(it) }
  }
}
