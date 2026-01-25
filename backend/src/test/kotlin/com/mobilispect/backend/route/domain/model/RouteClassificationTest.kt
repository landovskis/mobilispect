package com.mobilispect.backend.route.domain.model

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.junit.jupiter.params.provider.ValueSource

class RouteClassificationTest {

  @Nested
  inner class FromAverageSpacing {

    @Test
    fun `returns UNKNOWN for null spacing`() {
      val result = RouteClassification.fromAverageSpacing(null)
      assertThat(result).isEqualTo(RouteClassification.UNKNOWN)
    }

    @Test
    fun `returns UNKNOWN for NaN spacing`() {
      val result = RouteClassification.fromAverageSpacing(Double.NaN)
      assertThat(result).isEqualTo(RouteClassification.UNKNOWN)
    }

    @Test
    fun `returns UNKNOWN for negative spacing`() {
      val result = RouteClassification.fromAverageSpacing(-100.0)
      assertThat(result).isEqualTo(RouteClassification.UNKNOWN)
    }

    @ParameterizedTest
    @ValueSource(doubles = [0.0, 100.0, 200.0, 300.0, 399.99])
    fun `returns LOCAL for spacing under 400m`(meters: Double) {
      val result = RouteClassification.fromAverageSpacing(meters)
      assertThat(result).isEqualTo(RouteClassification.LOCAL)
    }

    @ParameterizedTest
    @ValueSource(doubles = [400.0, 500.0, 600.0, 700.0, 799.99])
    fun `returns LIMITED for spacing between 400m and 800m`(meters: Double) {
      val result = RouteClassification.fromAverageSpacing(meters)
      assertThat(result).isEqualTo(RouteClassification.LIMITED)
    }

    @ParameterizedTest
    @ValueSource(doubles = [800.0, 1000.0, 1200.0, 1400.0, 1499.99])
    fun `returns RAPID for spacing between 800m and 1500m`(meters: Double) {
      val result = RouteClassification.fromAverageSpacing(meters)
      assertThat(result).isEqualTo(RouteClassification.RAPID)
    }

    @ParameterizedTest
    @ValueSource(doubles = [1500.0, 2000.0, 2500.0, 2999.99])
    fun `returns SUBURBAN for spacing between 1500m and 3000m`(meters: Double) {
      val result = RouteClassification.fromAverageSpacing(meters)
      assertThat(result).isEqualTo(RouteClassification.SUBURBAN)
    }

    @ParameterizedTest
    @ValueSource(doubles = [3000.0, 3500.0, 4000.0, 4500.0, 4999.99])
    fun `returns REGIONAL for spacing between 3000m and 5000m`(meters: Double) {
      val result = RouteClassification.fromAverageSpacing(meters)
      assertThat(result).isEqualTo(RouteClassification.REGIONAL)
    }

    @ParameterizedTest
    @ValueSource(doubles = [5000.0, 6000.0, 7500.0, 9000.0, 9999.99])
    fun `returns EXPRESS for spacing between 5000m and 10000m`(meters: Double) {
      val result = RouteClassification.fromAverageSpacing(meters)
      assertThat(result).isEqualTo(RouteClassification.EXPRESS)
    }

    @ParameterizedTest
    @ValueSource(doubles = [10000.0, 15000.0, 25000.0, 50000.0])
    fun `returns REGIONAL_EXPRESS for spacing over 10000m`(meters: Double) {
      val result = RouteClassification.fromAverageSpacing(meters)
      assertThat(result).isEqualTo(RouteClassification.REGIONAL_EXPRESS)
    }

    @ParameterizedTest
    @CsvSource(
      "399.99, LOCAL",
      "400.0, LIMITED",
      "799.99, LIMITED",
      "800.0, RAPID",
      "1499.99, RAPID",
      "1500.0, SUBURBAN",
      "2999.99, SUBURBAN",
      "3000.0, REGIONAL",
      "4999.99, REGIONAL",
      "5000.0, EXPRESS",
      "9999.99, EXPRESS",
      "10000.0, REGIONAL_EXPRESS",
    )
    fun `correctly classifies at boundary values`(meters: Double, expectedClassification: String) {
      val result = RouteClassification.fromAverageSpacing(meters)
      assertThat(result.name).isEqualTo(expectedClassification)
    }
  }

  @Nested
  inner class EnumProperties {

    @Test
    fun `LOCAL has correct threshold values`() {
      assertThat(RouteClassification.LOCAL.minMeters).isEqualTo(0.0)
      assertThat(RouteClassification.LOCAL.maxMeters).isEqualTo(400.0)
    }

    @Test
    fun `LIMITED has correct threshold values`() {
      assertThat(RouteClassification.LIMITED.minMeters).isEqualTo(400.0)
      assertThat(RouteClassification.LIMITED.maxMeters).isEqualTo(800.0)
    }

    @Test
    fun `RAPID has correct threshold values`() {
      assertThat(RouteClassification.RAPID.minMeters).isEqualTo(800.0)
      assertThat(RouteClassification.RAPID.maxMeters).isEqualTo(1500.0)
    }

    @Test
    fun `SUBURBAN has correct threshold values`() {
      assertThat(RouteClassification.SUBURBAN.minMeters).isEqualTo(1500.0)
      assertThat(RouteClassification.SUBURBAN.maxMeters).isEqualTo(3000.0)
    }

    @Test
    fun `REGIONAL has correct threshold values`() {
      assertThat(RouteClassification.REGIONAL.minMeters).isEqualTo(3000.0)
      assertThat(RouteClassification.REGIONAL.maxMeters).isEqualTo(5000.0)
    }

    @Test
    fun `EXPRESS has correct threshold values`() {
      assertThat(RouteClassification.EXPRESS.minMeters).isEqualTo(5000.0)
      assertThat(RouteClassification.EXPRESS.maxMeters).isEqualTo(10000.0)
    }

    @Test
    fun `REGIONAL_EXPRESS has correct threshold values`() {
      assertThat(RouteClassification.REGIONAL_EXPRESS.minMeters).isEqualTo(10000.0)
      assertThat(RouteClassification.REGIONAL_EXPRESS.maxMeters).isNull()
    }

    @Test
    fun `UNKNOWN has NaN min and null max`() {
      assertThat(RouteClassification.UNKNOWN.minMeters).isNaN()
      assertThat(RouteClassification.UNKNOWN.maxMeters).isNull()
    }
  }

  @Nested
  inner class FromValue {

    @Test
    fun `returns enum for valid value string`() {
      assertThat(RouteClassification.fromValue("LOCAL")).isEqualTo(RouteClassification.LOCAL)
      assertThat(RouteClassification.fromValue("LIMITED")).isEqualTo(RouteClassification.LIMITED)
      assertThat(RouteClassification.fromValue("RAPID")).isEqualTo(RouteClassification.RAPID)
      assertThat(RouteClassification.fromValue("SUBURBAN")).isEqualTo(RouteClassification.SUBURBAN)
      assertThat(RouteClassification.fromValue("REGIONAL")).isEqualTo(RouteClassification.REGIONAL)
      assertThat(RouteClassification.fromValue("EXPRESS")).isEqualTo(RouteClassification.EXPRESS)
      assertThat(RouteClassification.fromValue("REGIONAL_EXPRESS"))
        .isEqualTo(RouteClassification.REGIONAL_EXPRESS)
      assertThat(RouteClassification.fromValue("UNKNOWN")).isEqualTo(RouteClassification.UNKNOWN)
    }

    @Test
    fun `throws for invalid value string`() {
      org.junit.jupiter.api.assertThrows<IllegalArgumentException> {
        RouteClassification.fromValue("INVALID")
      }
    }
  }
}
