package com.mobilispect.backend.route.domain.service

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import java.time.LocalTime

class ClockFaceDetectorTest {

  private val detector = ClockFaceDetector()

  @Nested
  inner class DetectClockFaceInterval {

    @Test
    fun `returns null for empty departure list`() {
      val result = detector.detect(emptyList())
      assertThat(result).isNull()
    }

    @Test
    fun `returns null for fewer than 6 departures`() {
      val departures = listOf(
        LocalTime.of(6, 0),
        LocalTime.of(6, 15),
        LocalTime.of(6, 30),
        LocalTime.of(6, 45),
        LocalTime.of(7, 0),
      )
      val result = detector.detect(departures)
      assertThat(result).isNull()
    }

    @Test
    fun `detects 15-minute clock-face interval`() {
      val departures = listOf(
        LocalTime.of(6, 0),
        LocalTime.of(6, 15),
        LocalTime.of(6, 30),
        LocalTime.of(6, 45),
        LocalTime.of(7, 0),
        LocalTime.of(7, 15),
        LocalTime.of(7, 30),
      )
      val result = detector.detect(departures)
      assertThat(result).isEqualTo(15)
    }

    @Test
    fun `detects 10-minute clock-face interval`() {
      val departures = listOf(
        LocalTime.of(8, 0),
        LocalTime.of(8, 10),
        LocalTime.of(8, 20),
        LocalTime.of(8, 30),
        LocalTime.of(8, 40),
        LocalTime.of(8, 50),
        LocalTime.of(9, 0),
      )
      val result = detector.detect(departures)
      assertThat(result).isEqualTo(10)
    }

    @Test
    fun `detects 20-minute clock-face interval`() {
      val departures = listOf(
        LocalTime.of(9, 0),
        LocalTime.of(9, 20),
        LocalTime.of(9, 40),
        LocalTime.of(10, 0),
        LocalTime.of(10, 20),
        LocalTime.of(10, 40),
      )
      val result = detector.detect(departures)
      assertThat(result).isEqualTo(20)
    }

    @Test
    fun `detects 30-minute clock-face interval`() {
      val departures = listOf(
        LocalTime.of(6, 0),
        LocalTime.of(6, 30),
        LocalTime.of(7, 0),
        LocalTime.of(7, 30),
        LocalTime.of(8, 0),
        LocalTime.of(8, 30),
      )
      val result = detector.detect(departures)
      assertThat(result).isEqualTo(30)
    }

    @Test
    fun `detects 60-minute clock-face interval`() {
      val departures = listOf(
        LocalTime.of(6, 0),
        LocalTime.of(7, 0),
        LocalTime.of(8, 0),
        LocalTime.of(9, 0),
        LocalTime.of(10, 0),
        LocalTime.of(11, 0),
      )
      val result = detector.detect(departures)
      assertThat(result).isEqualTo(60)
    }

    @Test
    fun `detects 12-minute clock-face interval`() {
      val departures = listOf(
        LocalTime.of(10, 0),
        LocalTime.of(10, 12),
        LocalTime.of(10, 24),
        LocalTime.of(10, 36),
        LocalTime.of(10, 48),
        LocalTime.of(11, 0),
      )
      val result = detector.detect(departures)
      assertThat(result).isEqualTo(12)
    }

    @Test
    fun `tolerates minor variations within 2 minutes`() {
      val departures = listOf(
        LocalTime.of(6, 0),
        LocalTime.of(6, 14), // 14 min (within ±2 of 15)
        LocalTime.of(6, 30),
        LocalTime.of(6, 46), // 16 min (within ±2 of 15)
        LocalTime.of(7, 0),
        LocalTime.of(7, 15),
      )
      val result = detector.detect(departures)
      assertThat(result).isEqualTo(15)
    }

    @Test
    fun `returns null for irregular schedule`() {
      val departures = listOf(
        LocalTime.of(6, 0),
        LocalTime.of(6, 12),
        LocalTime.of(6, 35),
        LocalTime.of(7, 5),
        LocalTime.of(7, 22),
        LocalTime.of(7, 55),
      )
      val result = detector.detect(departures)
      assertThat(result).isNull()
    }

    @Test
    fun `ignores overnight gaps greater than 90 minutes`() {
      val departures = listOf(
        LocalTime.of(22, 0),
        LocalTime.of(22, 15),
        LocalTime.of(22, 30),
        LocalTime.of(22, 45),
        LocalTime.of(23, 0),
        // Overnight gap would be to next day - simulated by large gap
        LocalTime.of(6, 0),
        LocalTime.of(6, 15),
        LocalTime.of(6, 30),
      )
      val result = detector.detect(departures)
      assertThat(result).isEqualTo(15)
    }

    @Test
    fun `handles unsorted departure times`() {
      val departures = listOf(
        LocalTime.of(7, 15),
        LocalTime.of(6, 30),
        LocalTime.of(7, 0),
        LocalTime.of(6, 0),
        LocalTime.of(6, 45),
        LocalTime.of(6, 15),
      )
      val result = detector.detect(departures)
      assertThat(result).isEqualTo(15)
    }

    @Test
    fun `requires at least 80 percent consistency`() {
      // 5 intervals: 15, 15, 15, 25, 15 = 4/5 = 80% -> should pass
      val departuresAt80Percent = listOf(
        LocalTime.of(6, 0),
        LocalTime.of(6, 15),
        LocalTime.of(6, 30),
        LocalTime.of(6, 45),
        LocalTime.of(7, 10), // 25 min gap
        LocalTime.of(7, 25),
      )
      val result80 = detector.detect(departuresAt80Percent)
      assertThat(result80).isEqualTo(15)

      // 5 intervals: 15, 15, 25, 25, 15 = 3/5 = 60% -> should fail
      val departuresBelow80 = listOf(
        LocalTime.of(6, 0),
        LocalTime.of(6, 15),
        LocalTime.of(6, 30),
        LocalTime.of(6, 55), // 25 min gap
        LocalTime.of(7, 20), // 25 min gap
        LocalTime.of(7, 35),
      )
      val resultBelow80 = detector.detect(departuresBelow80)
      assertThat(resultBelow80).isNull()
    }

    @Test
    fun `prefers smaller valid intervals when multiple match`() {
      // 6 intervals of 10 minutes also match as 20-minute intervals
      val departures = listOf(
        LocalTime.of(8, 0),
        LocalTime.of(8, 10),
        LocalTime.of(8, 20),
        LocalTime.of(8, 30),
        LocalTime.of(8, 40),
        LocalTime.of(8, 50),
        LocalTime.of(9, 0),
      )
      val result = detector.detect(departures)
      // Should detect 10 minutes (the actual interval), not 20
      assertThat(result).isEqualTo(10)
    }

    @ParameterizedTest
    @ValueSource(ints = [10, 12, 15, 20, 30, 60])
    fun `detects all valid clock-face intervals`(intervalMinutes: Int) {
      val departures = (0 until 6).map { i ->
        LocalTime.of(8, 0).plusMinutes((i * intervalMinutes).toLong())
      }
      val result = detector.detect(departures)
      assertThat(result).isEqualTo(intervalMinutes)
    }
  }
}
