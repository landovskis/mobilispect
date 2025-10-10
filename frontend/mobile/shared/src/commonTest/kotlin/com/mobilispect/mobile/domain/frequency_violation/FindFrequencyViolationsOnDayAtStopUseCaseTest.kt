package com.mobilispect.mobile.domain.frequency_violation

import com.mobilispect.mobile.data.frequency_commitment.STM_FREQUENCY_COMMITMENT
import com.mobilispect.mobile.data.schedule.Direction
import com.mobilispect.mobile.data.schedule.FakeScheduleRepository
import com.mobilispect.mobile.data.stop.StopRef
import kotlinx.datetime.LocalDateTime
import kotlin.test.Test

internal class FindFrequencyViolationsOnDayAtStopUseCaseTest {
    @Test
    fun violationsFromScheduledStops() {
        FindFrequencyViolationsOnDayAtStopUseCase(FakeScheduleRepository())
            .invoke(
                start = LocalDateTime(2022, 7, 7, 6, 0, 0),
                routeID = "r-f25em-141",
                stopRef = StopRef(geohash = "abcd", name = "test"),
                direction = Direction.Outbound,
                commitment = STM_FREQUENCY_COMMITMENT
            )
    }
}
