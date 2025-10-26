package com.mobilispect.backend.schedule

import com.mobilispect.backend.Feed
import com.mobilispect.backend.FeedVersion

/**
 * Represents a scheduled feed for transit data
 */
data class ScheduledFeed(
    val feed: Feed,
    val version: FeedVersion
)
