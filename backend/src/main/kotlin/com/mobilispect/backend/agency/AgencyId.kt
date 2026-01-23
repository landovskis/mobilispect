package com.mobilispect.backend.agency

import com.mobilispect.backend.feed.api.ids.FeedLocalAgencyId
import com.mobilispect.backend.feed.domain.model.ids.FeedId

private const val delimiter = "-"

/**
 * Value class for Agency identifiers. Ensures type safety and prevents ID mix-ups across domain
 * boundaries.
 *
 * Per constitutional Code Quality First requirements (FR-018).
 */
data class AgencyId(val value: String) {
  init {
    require(value.isNotBlank()) { "Agency ID cannot be blank" }
  }

  constructor(
    feedId: FeedId,
    agencyId: FeedLocalAgencyId,
  ) : this("${feedId.value}$delimiter${agencyId.value}")

  override fun toString(): String = value

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (other !is AgencyId) return false
    return value == other.value
  }

  override fun hashCode(): Int = value.hashCode()
}
