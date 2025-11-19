package com.mobilispect.backend.schedule

import com.mobilispect.backend.feed.model.FeedEntity
import com.mobilispect.backend.FeedVersion

/**
 * Represents a scheduled feed for transit data
 */
data class ScheduledFeed(
    val feed: FeedEntity,
    val version: FeedVersion
)
