package com.mobilispect.mobile.data.schedule

import com.mobilispect.mobile.data.stop.StopRef
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlin.time.Duration

data class ScheduledStop(val routeRef: String, val stopRef: StopRef, val time: Time) {
    sealed interface Time {
        data class Scheduled(val departsAt: LocalDateTime?, val arrivesAt: LocalDateTime?) : Time {
            val dateTime: LocalDateTime?
                get() = departsAt ?: arrivesAt
        }

        data class Frequency(val start: LocalTime, val end: LocalTime, val duration: Duration) :
            Time
    }
}
