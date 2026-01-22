package com.mobilispect.mobile.domain.frequency_violation

import com.mobilispect.mobile.data.frequency_commitment.FrequencyCommitment
import com.mobilispect.mobile.data.schedule.Direction
import com.mobilispect.mobile.data.schedule.ScheduleRepository
import com.mobilispect.mobile.data.schedule.ScheduledStop
import com.mobilispect.mobile.data.stop.StopRef
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlin.time.Duration
import kotlin.time.ExperimentalTime

@OptIn(ExperimentalTime::class)
class FindFrequencyViolationsOnDayAtStopUseCase(private val scheduleRepo: ScheduleRepository) {
    operator fun invoke(
        start: LocalDateTime,
        routeID: String,
        stopRef: StopRef,
        direction: Direction,
        commitment: FrequencyCommitment
    ): Result<List<FrequencyViolation>> {
        val relevantCommitments =
            commitment.spans.filter { span -> span.routes.contains(routeID) }
                .filter { span -> span.daysOfWeek.contains(start.dayOfWeek) }
        val relevantCommitment = relevantCommitments.firstOrNull() ?: return Result.success(
            emptyList()
        )

        val directionTimes =
            relevantCommitment.directions.filter { directionTime -> directionTime.direction == direction || directionTime.direction == null }
        /*.filter { directionTime ->
            start.dayOfWeek == start.toLocalDate().dayOfWeek
                    && !directionTime.start.isAfter(start.toLocalTime())
        }*/
        val directionTime = directionTimes.firstOrNull() ?: return Result.success(emptyList())

        val adjustedStart = LocalDateTime(start.date, directionTime.start)
        val adjustedEnd = LocalDateTime(start.date, directionTime.end)

        val sentinel = Duration.ZERO
        val departures = scheduleRepo.forDayAtStopOnRouteInDirection(
            adjustedStart,
            adjustedEnd,
            routeID,
            stopRef,
            direction
        )
        if (departures.isEmpty()) {
            return Result.failure(NoDeparturesFound)
        }

        //TODO: Determine what to do for frequency, especially for scheduled -> frequency and vice
        // versa
        val violations = departures.filter { stop -> stop.time is ScheduledStop.Time.Scheduled }
            .windowed(2, 1, true) { dateTimes ->
                return@windowed if (dateTimes.size >= 2) {
                    val intervalStart = localDateTime(dateTimes[0])
                    val intervalEnd = localDateTime(dateTimes[1])

                    if ((intervalStart != null) && (intervalEnd != null)) {
                        val duration = intervalEnd.toInstant(TimeZone.currentSystemDefault()) -
                                intervalStart.toInstant(TimeZone.currentSystemDefault())
                        return@windowed FrequencyViolation(
                            start = intervalStart,
                            end = intervalEnd, duration = duration
                        )
                    } else {
                        null
                    }
                } else {
                    null
                }
            }
            .filterNotNull()
            .filter { it.duration != sentinel }
            .filter {
                it.duration > relevantCommitment.frequency
            }

        println(departures)
        return Result.success(violations)
    }

    private fun localDateTime(dateTime: ScheduledStop) =
        (dateTime.time as ScheduledStop.Time.Scheduled).dateTime
}

data class FrequencyViolation(
    val start: LocalDateTime,
    val end: LocalDateTime,
    val duration: Duration
)

object NoDeparturesFound : Exception()
