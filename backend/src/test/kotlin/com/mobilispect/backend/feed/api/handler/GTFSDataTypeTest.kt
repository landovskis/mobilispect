package com.mobilispect.backend.feed.api.handler

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class GTFSDataTypeTest {

  @Test
  fun `should have all required GTFS data types`() {
    val expectedTypes =
      setOf(
        GTFSDataType.AGENCY,
        GTFSDataType.ROUTE,
        GTFSDataType.TRIP,
        GTFSDataType.STOP,
        GTFSDataType.SHAPE,
        GTFSDataType.STOP_TIME,
        GTFSDataType.FREQUENCY,
        GTFSDataType.CALENDAR,
      )

    assertThat(GTFSDataType.entries.toSet()).isEqualTo(expectedTypes)
  }

  @Test
  fun `should have exactly 8 data types`() {
    assertThat(GTFSDataType.entries).hasSize(8)
  }
}
