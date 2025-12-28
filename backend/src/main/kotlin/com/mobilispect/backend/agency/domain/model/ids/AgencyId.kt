package com.mobilispect.backend.agency.domain.model.ids

import com.mobilispect.backend.feed.api.ids.GTFSAgencyId
import com.mobilispect.backend.feed.domain.model.ids.FeedId

/**
 * Value class for Agency identifiers using Onestop ID format.
 * Ensures type safety and prevents ID mixups across domain boundaries.
 *
 * Format: o-{feedId}-{gtfsAgencyId} (e.g., o-f-abc-agency123)
 *
 * Per constitutional Code Quality First requirements (FR-018).
 *
 * Now using @JvmInline for zero-overhead type safety in the domain layer.
 * Data layer uses plain String IDs for Hibernate 7 compatibility.
 */
@JvmInline
value class AgencyId(val value: String) {
    init {
        require(value.isNotBlank()) { "Agency ID cannot be blank" }
        require(value.startsWith("o-")) { "Agency ID must start with 'o-' prefix" }
    }

    override fun toString(): String = value

    companion object {
        /**
         * Creates an AgencyId from FeedId and GTFSAgencyId components.
         * Constructs the Onestop ID in the format: o-{feedId}-{gtfsAgencyId}
         */
        fun of(feedId: FeedId, gtfsAgencyId: GTFSAgencyId): AgencyId {
            return AgencyId("o-${feedId.value}-${gtfsAgencyId.value}")
        }

        /**
         * Creates an AgencyId from a nullable string value.
         */
        fun from(value: String?): AgencyId? =
            value?.takeIf { it.isNotBlank() }?.let { AgencyId(it) }
    }
}
