package com.mobilispect.mobile.domain.time

internal class FormatTimeUseCaseTest {
    val subject = FormatTimeUseCase(locale = Locale.CANADA)

    @Test
    fun givenValidDateInEvening() {
        val actual = subject(LocalDateTime.of(2022, 7, 20, 23, 59))

        Truth.assertThat(actual).isEqualTo("11:59 p.m.")
    }

    @Test
    fun givenValidDateInMorning() {
        val actual = subject(LocalDateTime.of(2022, 7, 20, 11, 59))
        Truth.assertThat(actual).isEqualTo("11:59 a.m.")
    }

    @Test
    fun saturatesAtMinimum() {
        val actual = subject(LocalDateTime.MIN)
        Truth.assertThat(actual).isEqualTo("12:00 a.m.")
    }

    @Test
    fun saturatesAtMaximum() {
        val actual = subject(LocalDateTime.MAX)
        Truth.assertThat(actual).isEqualTo("11:59 p.m.")
    }

}
