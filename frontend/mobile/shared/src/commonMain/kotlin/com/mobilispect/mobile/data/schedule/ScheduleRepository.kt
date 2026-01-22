package com.mobilispect.mobile.data.schedule

import com.mobilispect.mobile.data.stop.StopRef
import kotlinx.datetime.LocalDateTime

interface ScheduleRepository {
    fun forDayAtStopOnRouteInDirection(
        start: LocalDateTime,
        end: LocalDateTime,
        routeRef: String,
        stopRef: StopRef,
        direction: Direction
    ): Collection<ScheduledStop>
}
