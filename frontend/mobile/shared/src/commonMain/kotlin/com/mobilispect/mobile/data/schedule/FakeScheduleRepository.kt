package com.mobilispect.mobile.data.schedule

import com.mobilispect.mobile.data.stop.StopRef
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.plus

internal class FakeScheduleRepository : ScheduleRepository {
    override fun forDayAtStopOnRouteInDirection(
        start: LocalDateTime,
        end: LocalDateTime,
        routeRef: String,
        stopRef: StopRef,
        direction: Direction
    ): Collection<ScheduledStop> {
        val today = start.date
        val departures = when (direction) {
            Direction.Inbound -> inboundDepartures(
                routeRef = routeRef,
                stopRef = stopRef,
                today = today
            )
            Direction.Outbound -> outboundDepartures(
                routeRef = routeRef,
                stopRef = stopRef,
                today = today
            )
        }

        return departures.filter { stop ->
            val time = (stop.time as ScheduledStop.Time.Scheduled).dateTime ?: return@filter false
            return@filter time >= start && time < end
        }
    }

    private fun inboundDepartures(
        routeRef: String,
        stopRef: StopRef,
        today: LocalDate
    ): List<ScheduledStop> {
        val tomorrow = today.plus(DatePeriod(days = 1))
        return listOf(
            LocalDateTime(today, LocalTime(4, 45)),
            LocalDateTime(today, LocalTime(4, 57)),

            LocalDateTime(today, LocalTime(5, 10)),
            LocalDateTime(today, LocalTime(5, 22)),
            LocalDateTime(today, LocalTime(5, 34)),
            LocalDateTime(today, LocalTime(5, 45)),
            LocalDateTime(today, LocalTime(5, 57)),

            LocalDateTime(today, LocalTime(6, 9)),
            LocalDateTime(today, LocalTime(6, 19)),
            LocalDateTime(today, LocalTime(6, 29)),
            LocalDateTime(today, LocalTime(6, 38)),
            LocalDateTime(today, LocalTime(6, 48)),
            LocalDateTime(today, LocalTime(6, 58)),

            LocalDateTime(today, LocalTime(7, 8)),
            LocalDateTime(today, LocalTime(7, 18)),
            LocalDateTime(today, LocalTime(7, 28)),
            LocalDateTime(today, LocalTime(7, 39)),
            LocalDateTime(today, LocalTime(7, 49)),

            LocalDateTime(today, LocalTime(8, 0)),
            LocalDateTime(today, LocalTime(8, 11)),
            LocalDateTime(today, LocalTime(8, 22)),
            LocalDateTime(today, LocalTime(8, 35)),
            LocalDateTime(today, LocalTime(8, 48)),

            LocalDateTime(today, LocalTime(9, 1)),
            LocalDateTime(today, LocalTime(9, 13)),
            LocalDateTime(today, LocalTime(9, 24)),
            LocalDateTime(today, LocalTime(9, 36)),
            LocalDateTime(today, LocalTime(9, 48)),

            LocalDateTime(today, LocalTime(10, 1)),
            LocalDateTime(today, LocalTime(10, 14)),
            LocalDateTime(today, LocalTime(10, 26)),
            LocalDateTime(today, LocalTime(10, 36)),
            LocalDateTime(today, LocalTime(10, 46)),
            LocalDateTime(today, LocalTime(10, 56)),

            LocalDateTime(today, LocalTime(11, 6)),
            LocalDateTime(today, LocalTime(11, 16)),
            LocalDateTime(today, LocalTime(11, 26)),
            LocalDateTime(today, LocalTime(11, 35)),
            LocalDateTime(today, LocalTime(11, 43)),
            LocalDateTime(today, LocalTime(11, 51)),
            LocalDateTime(today, LocalTime(11, 59)),

            LocalDateTime(today, LocalTime(12, 8)),
            LocalDateTime(today, LocalTime(12, 17)),
            LocalDateTime(today, LocalTime(12, 25)),
            LocalDateTime(today, LocalTime(12, 32)),
            LocalDateTime(today, LocalTime(12, 38)),
            LocalDateTime(today, LocalTime(12, 45)),
            LocalDateTime(today, LocalTime(12, 52)),
            LocalDateTime(today, LocalTime(12, 58)),

            LocalDateTime(today, LocalTime(13, 6)),
            LocalDateTime(today, LocalTime(13, 13)),
            LocalDateTime(today, LocalTime(13, 20)),
            LocalDateTime(today, LocalTime(13, 27)),
            LocalDateTime(today, LocalTime(13, 35)),
            LocalDateTime(today, LocalTime(13, 43)),
            LocalDateTime(today, LocalTime(13, 51)),
            LocalDateTime(today, LocalTime(13, 59)),

            LocalDateTime(today, LocalTime(14, 7)),
            LocalDateTime(today, LocalTime(14, 15)),
            LocalDateTime(today, LocalTime(14, 23)),
            LocalDateTime(today, LocalTime(14, 32)),
            LocalDateTime(today, LocalTime(14, 42)),
            LocalDateTime(today, LocalTime(14, 50)),
            LocalDateTime(today, LocalTime(14, 57)),

            LocalDateTime(today, LocalTime(15, 4)),
            LocalDateTime(today, LocalTime(15, 10)),
            LocalDateTime(today, LocalTime(15, 15)),
            LocalDateTime(today, LocalTime(15, 21)),
            LocalDateTime(today, LocalTime(15, 29)),
            LocalDateTime(today, LocalTime(15, 36)),
            LocalDateTime(today, LocalTime(15, 44)),
            LocalDateTime(today, LocalTime(15, 52)),

            LocalDateTime(today, LocalTime(16, 0)),
            LocalDateTime(today, LocalTime(16, 7)),
            LocalDateTime(today, LocalTime(16, 15)),
            LocalDateTime(today, LocalTime(16, 22)),
            LocalDateTime(today, LocalTime(16, 28)),
            LocalDateTime(today, LocalTime(16, 34)),
            LocalDateTime(today, LocalTime(16, 40)),
            LocalDateTime(today, LocalTime(16, 47)),
            LocalDateTime(today, LocalTime(16, 54)),

            LocalDateTime(today, LocalTime(17, 1)),
            LocalDateTime(today, LocalTime(17, 11)),
            LocalDateTime(today, LocalTime(17, 21)),
            LocalDateTime(today, LocalTime(17, 31)),
            LocalDateTime(today, LocalTime(17, 41)),
            LocalDateTime(today, LocalTime(17, 50)),

            LocalDateTime(today, LocalTime(18, 0)),
            LocalDateTime(today, LocalTime(18, 9)),
            LocalDateTime(today, LocalTime(18, 19)),
            LocalDateTime(today, LocalTime(18, 29)),
            LocalDateTime(today, LocalTime(18, 40)),
            LocalDateTime(today, LocalTime(18, 52)),

            LocalDateTime(today, LocalTime(19, 4)),
            LocalDateTime(today, LocalTime(19, 17)),
            LocalDateTime(today, LocalTime(19, 30)),
            LocalDateTime(today, LocalTime(19, 43)),
            LocalDateTime(today, LocalTime(19, 56)),

            LocalDateTime(today, LocalTime(20, 7)),
            LocalDateTime(today, LocalTime(20, 19)),
            LocalDateTime(today, LocalTime(20, 31)),
            LocalDateTime(today, LocalTime(20, 43)),
            LocalDateTime(today, LocalTime(20, 56)),

            LocalDateTime(today, LocalTime(21, 10)),
            LocalDateTime(today, LocalTime(21, 28)),
            LocalDateTime(today, LocalTime(21, 48)),

            LocalDateTime(today, LocalTime(22, 8)),
            LocalDateTime(today, LocalTime(22, 29)),
            LocalDateTime(today, LocalTime(22, 50)),

            LocalDateTime(today, LocalTime(23, 11)),
            LocalDateTime(today, LocalTime(23, 33)),
            LocalDateTime(today, LocalTime(23, 59)),

            LocalDateTime(tomorrow, LocalTime(0, 27)),
            LocalDateTime(tomorrow, LocalTime(0, 55)),

            LocalDateTime(tomorrow, LocalTime(1, 22)),
        )
            .map { time ->
                ScheduledStop(
                    routeRef = routeRef,
                    stopRef = stopRef,
                    time = ScheduledStop.Time.Scheduled(
                        departsAt = time,
                        arrivesAt = time,
                    )
                )
            }
    }

    private fun outboundDepartures(
        routeRef: String,
        stopRef: StopRef,
        today: LocalDate
    ): List<ScheduledStop> {
        val tomorrow = today.plus(DatePeriod(days = 1))
        return listOf(
            LocalDateTime(today, LocalTime(4, 48)),

            LocalDateTime(today, LocalTime(5, 9)),
            LocalDateTime(today, LocalTime(5, 23)),
            LocalDateTime(today, LocalTime(5, 43)),

            LocalDateTime(today, LocalTime(6, 0)),
            LocalDateTime(today, LocalTime(6, 14)),
            LocalDateTime(today, LocalTime(6, 25)),
            LocalDateTime(today, LocalTime(6, 36)),
            LocalDateTime(today, LocalTime(6, 47)),

            LocalDateTime(today, LocalTime(7, 10)),
            LocalDateTime(today, LocalTime(7, 26)),
            LocalDateTime(today, LocalTime(7, 43)),

            LocalDateTime(today, LocalTime(8, 0)),
            LocalDateTime(today, LocalTime(8, 14)),
            LocalDateTime(today, LocalTime(8, 27)),
            LocalDateTime(today, LocalTime(8, 40)),
            LocalDateTime(today, LocalTime(8, 52)),

            LocalDateTime(today, LocalTime(9, 3)),
            LocalDateTime(today, LocalTime(9, 13)),
            LocalDateTime(today, LocalTime(9, 23)),
            LocalDateTime(today, LocalTime(9, 33)),
            LocalDateTime(today, LocalTime(9, 43)),
            LocalDateTime(today, LocalTime(9, 53)),

            LocalDateTime(today, LocalTime(10, 3)),
            LocalDateTime(today, LocalTime(10, 13)),
            LocalDateTime(today, LocalTime(10, 23)),
            LocalDateTime(today, LocalTime(10, 33)),
            LocalDateTime(today, LocalTime(10, 42)),
            LocalDateTime(today, LocalTime(10, 50)),
            LocalDateTime(today, LocalTime(10, 58)),

            LocalDateTime(today, LocalTime(11, 6)),
            LocalDateTime(today, LocalTime(11, 15)),
            LocalDateTime(today, LocalTime(11, 23)),
            LocalDateTime(today, LocalTime(11, 31)),
            LocalDateTime(today, LocalTime(11, 38)),
            LocalDateTime(today, LocalTime(11, 44)),
            LocalDateTime(today, LocalTime(11, 51)),

            LocalDateTime(today, LocalTime(12, 0)),
            LocalDateTime(today, LocalTime(12, 9)),
            LocalDateTime(today, LocalTime(12, 17)),
            LocalDateTime(today, LocalTime(12, 25)),
            LocalDateTime(today, LocalTime(12, 33)),
            LocalDateTime(today, LocalTime(12, 41)),
            LocalDateTime(today, LocalTime(12, 49)),
            LocalDateTime(today, LocalTime(12, 57)),

            LocalDateTime(today, LocalTime(13, 5)),
            LocalDateTime(today, LocalTime(13, 13)),
            LocalDateTime(today, LocalTime(13, 20)),
            LocalDateTime(today, LocalTime(13, 27)),
            LocalDateTime(today, LocalTime(13, 34)),
            LocalDateTime(today, LocalTime(13, 40)),
            LocalDateTime(today, LocalTime(13, 48)),
            LocalDateTime(today, LocalTime(13, 55)),

            LocalDateTime(today, LocalTime(14, 3)),
            LocalDateTime(today, LocalTime(14, 11)),
            LocalDateTime(today, LocalTime(14, 18)),
            LocalDateTime(today, LocalTime(14, 25)),
            LocalDateTime(today, LocalTime(14, 33)),
            LocalDateTime(today, LocalTime(14, 40)),
            LocalDateTime(today, LocalTime(14, 47)),
            LocalDateTime(today, LocalTime(14, 55)),

            LocalDateTime(today, LocalTime(15, 3)),
            LocalDateTime(today, LocalTime(15, 10)),
            LocalDateTime(today, LocalTime(15, 17)),
            LocalDateTime(today, LocalTime(15, 23)),
            LocalDateTime(today, LocalTime(15, 29)),
            LocalDateTime(today, LocalTime(15, 35)),
            LocalDateTime(today, LocalTime(15, 42)),
            LocalDateTime(today, LocalTime(15, 49)),
            LocalDateTime(today, LocalTime(15, 56)),

            LocalDateTime(today, LocalTime(16, 3)),
            LocalDateTime(today, LocalTime(16, 10)),
            LocalDateTime(today, LocalTime(16, 17)),
            LocalDateTime(today, LocalTime(16, 23)),
            LocalDateTime(today, LocalTime(16, 29)),
            LocalDateTime(today, LocalTime(16, 34)),
            LocalDateTime(today, LocalTime(16, 39)),
            LocalDateTime(today, LocalTime(16, 44)),
            LocalDateTime(today, LocalTime(16, 50)),
            LocalDateTime(today, LocalTime(16, 55)),

            LocalDateTime(today, LocalTime(17, 0)),
            LocalDateTime(today, LocalTime(17, 7)),
            LocalDateTime(today, LocalTime(17, 13)),
            LocalDateTime(today, LocalTime(17, 20)),
            LocalDateTime(today, LocalTime(17, 28)),
            LocalDateTime(today, LocalTime(17, 35)),
            LocalDateTime(today, LocalTime(17, 41)),
            LocalDateTime(today, LocalTime(17, 47)),
            LocalDateTime(today, LocalTime(17, 54)),

            LocalDateTime(today, LocalTime(18, 1)),
            LocalDateTime(today, LocalTime(18, 7)),
            LocalDateTime(today, LocalTime(18, 13)),
            LocalDateTime(today, LocalTime(18, 20)),
            LocalDateTime(today, LocalTime(18, 28)),
            LocalDateTime(today, LocalTime(18, 36)),
            LocalDateTime(today, LocalTime(18, 44)),
            LocalDateTime(today, LocalTime(18, 53)),

            LocalDateTime(today, LocalTime(19, 3)),
            LocalDateTime(today, LocalTime(19, 12)),
            LocalDateTime(today, LocalTime(19, 21)),
            LocalDateTime(today, LocalTime(19, 31)),
            LocalDateTime(today, LocalTime(19, 41)),
            LocalDateTime(today, LocalTime(19, 52)),

            LocalDateTime(today, LocalTime(20, 5)),
            LocalDateTime(today, LocalTime(20, 19)),
            LocalDateTime(today, LocalTime(20, 34)),
            LocalDateTime(today, LocalTime(20, 49)),

            LocalDateTime(today, LocalTime(21, 3)),
            LocalDateTime(today, LocalTime(21, 16)),
            LocalDateTime(today, LocalTime(21, 30)),
            LocalDateTime(today, LocalTime(21, 44)),

            LocalDateTime(today, LocalTime(22, 3)),
            LocalDateTime(today, LocalTime(22, 22)),
            LocalDateTime(today, LocalTime(22, 41)),

            LocalDateTime(today, LocalTime(23, 1)),
            LocalDateTime(today, LocalTime(23, 21)),
            LocalDateTime(today, LocalTime(23, 43)),

            LocalDateTime(tomorrow, LocalTime(0, 10)),
            LocalDateTime(tomorrow, LocalTime(0, 37)),
        ).map { time ->
            ScheduledStop(
                routeRef = routeRef,
                stopRef = stopRef,
                time = ScheduledStop.Time.Scheduled(
                    departsAt = time,
                    arrivesAt = time,
                )
            )
        }
    }
}
