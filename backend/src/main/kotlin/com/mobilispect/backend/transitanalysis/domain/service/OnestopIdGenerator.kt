package com.mobilispect.backend.transitanalysis.domain.service

import com.mobilispect.backend.feed.model.ids.FeedId
import com.mobilispect.backend.transitanalysis.domain.model.ids.StopId
import org.springframework.stereotype.Service

/**
 * Service for generating Transitland Onestop IDs for transit stops.
 *
 * Onestop IDs follow the format: s-{geohash}-{normalized_name}
 * - s: prefix indicating this is a stop/station
 * - geohash: extracted from the feed's Onestop ID for regional grouping
 * - normalized_name: lowercase alphanumeric with tildes for spaces
 *
 * Example: "s-9q8y-market~st" for Market St station in SF (geohash 9q8y)
 */
interface OnestopIdGenerator {

    /**
     * Generate a Stop Onestop ID from GTFS data.
     *
     * @param feedId Feed Onestop ID (e.g., "f-9q8y-sfmta")
     * @param gtfsStopId Original GTFS stop_id (not used in Onestop ID but provided for context)
     * @param name Stop name from GTFS stops.txt
     * @param lat Latitude (for future geohash generation if needed)
     * @param lon Longitude (for future geohash generation if needed)
     * @return Stop Onestop ID (e.g., "s-9q8y-market~st")
     */
    fun generateStopId(feedId: FeedId, gtfsStopId: String, name: String, lat: Double, lon: Double): StopId

    /**
     * Normalize a stop name for use in Onestop ID.
     *
     * Normalization rules:
     * - Convert to lowercase
     * - Replace non-alphanumeric characters with tildes (~)
     * - Collapse multiple consecutive tildes into one
     * - Remove leading/trailing tildes
     * - Truncate to 100 characters max
     *
     * @param name Original stop name
     * @return Normalized name suitable for Onestop ID
     */
    fun normalizeNameForOnestopId(name: String): String
}

/**
 * Default implementation of OnestopIdGenerator.
 *
 * Extracts geohash from feed Onestop ID and combines with normalized stop name.
 */
@Service
class OnestopIdGeneratorImpl : OnestopIdGenerator {

    override fun generateStopId(feedId: FeedId, gtfsStopId: String, name: String, lat: Double, lon: Double): StopId {
        // Extract geohash from feed Onestop ID
        // Feed ID format: f-{geohash}-{name}
        // Example: "f-9q8y-sfmta" -> geohash = "9q8y"
        val geohash = extractGeohashFromFeedId(feedId)

        // Normalize stop name
        val normalizedName = normalizeNameForOnestopId(name)

        // Combine into Stop Onestop ID format: s-{geohash}-{name}
        return StopId("s-$geohash-$normalizedName")
    }

    override fun normalizeNameForOnestopId(name: String): String {
        // Convert to lowercase
        var normalized = name.lowercase()

        // Replace all non-alphanumeric characters with tildes
        normalized = normalized.replace(Regex("[^a-z0-9]+"), "~")

        // Remove leading and trailing tildes
        normalized = normalized.trim('~')

        // Truncate to max 100 characters to keep IDs reasonable
        if (normalized.length > 100) {
            normalized = normalized.substring(0, 100).trimEnd('~')
        }

        return normalized
    }

    /**
     * Extract geohash from feed Onestop ID.
     *
     * Feed Onestop ID format: f-{geohash}-{name} or f-{geohash}-{name}~{variant}
     * Examples:
     * - "f-9q8y-sfmta" -> "9q8y"
     * - "f-9q9-caltrain~local" -> "9q9"
     * - "f-dr5reg-wmata" -> "dr5reg"
     */
    private fun extractGeohashFromFeedId(feedId: FeedId): String {
        val feedIdValue = feedId.value

        // Remove "f-" prefix
        val afterPrefix = feedIdValue.substringAfter("f-")

        // Extract geohash (everything before the second dash)
        val geohash = afterPrefix.substringBefore("-")

        return geohash
    }
}
