package com.mobilispect.backend.transitanalysis.contract

import java.nio.file.Files
import java.nio.file.Paths
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class FrequencyApiContractYamlTest {

  @Test
  fun `frequency api contract file defines expected endpoints`() {
    val path = Paths.get("../specs/003-transit-route-frequency/contracts/frequency-api.yaml")
    val content = Files.readString(path)

    // Basic sanity checks that key paths are present in the contract
    assertThat(content).contains("/routes/{routeId}")
    assertThat(content).contains("/routes/{routeId}/variants")
    assertThat(content).contains("/routes/{routeId}/common-sections")
    assertThat(content).contains("/common-sections/{sectionId}/frequency")
  }
}
