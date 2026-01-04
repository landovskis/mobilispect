package com.mobilispect.backend.agency.domain.model.ids

import com.mobilispect.backend.feed.api.ids.FeedLocalAgencyId
import com.mobilispect.backend.feed.domain.model.ids.FeedId

/**
 * Value class for Agency identifiers. Ensures type safety and prevents ID
 * mix-ups across domain boundaries.
 *
 * Per constitutional Code Quality First requirements (FR-018).
 *
 */
class AgencyId {
  var value: String

  constructor(value: String) {
    this.value = value
  }

  constructor(feedId: FeedId, agencyId: FeedLocalAgencyId) : this( "${feedId.value}-${agencyId.value}")

  override fun toString(): String = value
}
