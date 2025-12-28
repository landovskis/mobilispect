package com.mobilispect.backend

import com.mobilispect.backend.schedule.ScheduledFeed

interface FeedDataSource {
  fun feeds(region: String): Collection<Result<ScheduledFeed>>
}
