package com.mobilispect.mobile.domain.time

import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.format

class FormatTimeUseCase(private val timeZone: TimeZone) {
    operator fun invoke(dateTime: LocalDateTime): String =
        dateTime.format(LocalDateTime.Formats.ISO)
}
