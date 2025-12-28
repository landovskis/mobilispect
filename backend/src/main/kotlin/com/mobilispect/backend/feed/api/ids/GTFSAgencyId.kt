package com.mobilispect.backend.feed.api.ids

/**
 * Value class for GTFS agency identifiers from agency.txt files.
 * Ensures type safety and prevents confusion with Transitland Onestop agency IDs.
 *
 * This represents the raw agency_id from GTFS feeds, which is different from
 * the Transitland Onestop ID format (o-geohash-name).
 *
 * Example GTFS agency IDs: "CITPI", "STM", "RTL", "1"
 *
 * Per constitutional Code Quality First requirements (FR-018).
 *
 * Using @JvmInline for zero-overhead type safety in the domain layer.
 */
@JvmInline
value class GTFSAgencyId(val value: String) {
    init {
        require(value.isNotBlank()) { "GTFS Agency ID cannot be blank" }
    }

    override fun toString(): String = value

    companion object {
        fun from(value: String?): GTFSAgencyId? =
            value?.takeIf { it.isNotBlank() }?.let { GTFSAgencyId(it) }
    }
}
