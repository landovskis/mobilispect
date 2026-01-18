package com.mobilispect.backend.region.domain

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource

class RegionImportStatusTest {

  @ParameterizedTest
  @CsvSource(
    "pending, PENDING",
    "running, RUNNING",
    "completed, COMPLETED",
    "partial_success, PARTIAL_SUCCESS",
    "failed, FAILED",
    "cancelled, CANCELLED",
  )
  fun `fromDb maps database values to enum`(dbValue: String, expectedName: String) {
    val result = RegionImportStatus.fromDb(dbValue)

    assertThat(result?.name).isEqualTo(expectedName)
  }

  @Test
  fun `fromDb returns null for unknown value`() {
    val result = RegionImportStatus.fromDb("unknown")

    assertThat(result).isNull()
  }

  @Test
  fun `fromDb returns null for null input`() {
    val result = RegionImportStatus.fromDb(null)

    assertThat(result).isNull()
  }

  @ParameterizedTest
  @CsvSource(
    "PENDING, pending",
    "RUNNING, running",
    "COMPLETED, completed",
    "PARTIAL_SUCCESS, partial_success",
    "FAILED, failed",
    "CANCELLED, cancelled",
  )
  fun `dbValue returns correct database representation`(enumName: String, expectedDbValue: String) {
    val status = RegionImportStatus.valueOf(enumName)

    assertThat(status.dbValue).isEqualTo(expectedDbValue)
  }

  @ParameterizedTest
  @CsvSource(
    "PENDING, false",
    "RUNNING, false",
    "COMPLETED, true",
    "PARTIAL_SUCCESS, true",
    "FAILED, true",
    "CANCELLED, true",
  )
  fun `isTerminal correctly identifies terminal states`(enumName: String, expected: Boolean) {
    val status = RegionImportStatus.valueOf(enumName)

    assertThat(status.isTerminal()).isEqualTo(expected)
  }

  @ParameterizedTest
  @CsvSource(
    "PENDING, true",
    "RUNNING, true",
    "COMPLETED, false",
    "PARTIAL_SUCCESS, false",
    "FAILED, false",
    "CANCELLED, false",
  )
  fun `isActive correctly identifies active states`(enumName: String, expected: Boolean) {
    val status = RegionImportStatus.valueOf(enumName)

    assertThat(status.isActive()).isEqualTo(expected)
  }
}
