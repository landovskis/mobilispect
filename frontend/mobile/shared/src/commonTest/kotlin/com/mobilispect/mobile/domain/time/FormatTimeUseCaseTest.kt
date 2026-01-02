package com.mobilispect.mobile.domain.time

import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.format
import kotlin.test.Test
import kotlin.test.assertEquals

class FormatTimeUseCaseTest {
    private val subject = FormatTimeUseCase(TimeZone.UTC)

    @Test
    fun formatsEveningTime() {
        val dateTime = LocalDateTime(2022, 7, 20, 23, 59, 0)

        val actual = subject(dateTime)

        assertEquals(dateTime.format(LocalDateTime.Formats.ISO), actual)
    }

    @Test
    fun formatsMorningTime() {
        val dateTime = LocalDateTime(2022, 7, 20, 11, 59, 0)

        val actual = subject(dateTime)

        assertEquals(dateTime.format(LocalDateTime.Formats.ISO), actual)
    }
}
