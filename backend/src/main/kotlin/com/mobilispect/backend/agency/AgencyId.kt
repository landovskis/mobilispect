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
class AgencyId {
  var value: String

  constructor(value: String) {
    this.value = value
  }

  constructor(
    feedId: FeedId,
    agencyId: FeedLocalAgencyId,
  ) : this("${feedId.value}$delimiter${agencyId.value}")

  override fun toString(): String = value
}
