package com.mobilispect.backend.schedule

import com.mobilispect.backend.FeedVersion
import com.mobilispect.backend.feed.model.FeedEntity

/** Represents a scheduled feed for transit data */
data class ScheduledFeed(val feed: FeedEntity, val version: FeedVersion)
