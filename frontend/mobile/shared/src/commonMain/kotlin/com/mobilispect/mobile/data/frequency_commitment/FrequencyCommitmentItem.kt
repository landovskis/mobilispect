package com.mobilispect.mobile.data.frequency_commitment

import com.mobilispect.mobile.data.schedule.Direction
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalTime
import kotlin.time.Duration


data class DirectionTime(
    val direction: Direction?,
    val start: LocalTime,
    val end: LocalTime
) {

    companion object {
        fun both(start: LocalTime, end: LocalTime): Collection<DirectionTime> {
            return listOf(
                DirectionTime(
                    direction = null,
                    start = start,
                    end = end
                ),
            )
        }

        fun isBothDirections(direction: Direction?): Boolean = direction == null
    }
}

data class FrequencyCommitmentItem(
    val daysOfWeek: Collection<DayOfWeek>,
    val frequency: Duration,
    val routes: List<String>,
    val directions: Collection<DirectionTime>,
)
