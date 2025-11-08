package com.mobilispect.backend.schedule

import com.mobilispect.backend.Feed
import com.mobilispect.backend.FeedVersion

/**
 * Pairing of a feed definition with its specific schedule version.
 */
data class ScheduledFeed(
    val feed: Feed,
    val version: FeedVersion
)
