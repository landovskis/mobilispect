package com.mobilispect.backend

import com.mobilispect.backend.schedule.ScheduledFeed
import org.springframework.stereotype.Component
import java.time.LocalDate

@Suppress("MagicNumber")
@Component
class DefaultFeedDataSource : FeedDataSource {
    override fun feeds(region: String): Collection<Result<ScheduledFeed>> =
        listOf(
            Result.success(
                ScheduledFeed(
                    feed = Feed(
                        uid = "f-f256-exo~citlapresquîle",
                        url = "https://exo.quebec/xdata/citpi/google_transit.zip"
                    ),
                    version = FeedVersion(
                        uid = "a2b4c6",
                        feedID = "f-f256-exo~citlapresquîle",
                        startsOn = LocalDate.of(2022, 11, 23),
                        endsOn = LocalDate.of(2023, 6, 25)
                    )
                )
        )
    )
}
