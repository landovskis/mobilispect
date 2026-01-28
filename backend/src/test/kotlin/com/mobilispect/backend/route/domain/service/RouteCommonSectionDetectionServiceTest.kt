package com.mobilispect.backend.route.domain.service

import com.mobilispect.backend.route.RouteId
import com.mobilispect.backend.route.domain.model.RouteVariant
import com.mobilispect.backend.route.domain.model.ids.VariantHash
import java.time.Instant
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class RouteCommonSectionDetectionServiceTest {

  private val service: RouteCommonSectionDetectionService =
    RouteCommonSectionDetectionServiceImpl()

  @Test
  fun `should return null when no variants provided`() {
    val result = service.detectCommonSection(emptyList())
    assertNull(result)
  }

  @Test
  fun `should return null when only one variant provided`() {
    val variant =
      createVariant("variant1", "stop1|stop2|stop3", "Stop 1|Stop 2|Stop 3")
    val result = service.detectCommonSection(listOf(variant))
    assertNull(result)
  }

  @Test
  fun `should find common section for identical stop patterns`() {
    val variants =
      listOf(
        createVariant("variant1", "stop1|stop2|stop3", "Stop 1|Stop 2|Stop 3"),
        createVariant("variant2", "stop1|stop2|stop3", "Stop 1|Stop 2|Stop 3"),
      )

    val result = service.detectCommonSection(variants)

    assertNotNull(result)
    assertEquals("stop1|stop2|stop3", result!!.stopIds)
    assertEquals(listOf("Stop 1", "Stop 2", "Stop 3"), result.stopNames)
  }

  @Test
  fun `should find longest common section when variants have different lengths`() {
    val variants =
      listOf(
        createVariant("variant1", "stop1|stop2|stop3|stop4", "Stop 1|Stop 2|Stop 3|Stop 4"),
        createVariant("variant2", "stop1|stop2|stop3|stop5", "Stop 1|Stop 2|Stop 3|Stop 5"),
        createVariant("variant3", "stop0|stop1|stop2|stop3", "Stop 0|Stop 1|Stop 2|Stop 3"),
      )

    val result = service.detectCommonSection(variants)

    assertNotNull(result)
    assertEquals("stop1|stop2|stop3", result!!.stopIds)
    assertEquals(listOf("Stop 1", "Stop 2", "Stop 3"), result.stopNames)
  }

  @Test
  fun `should find common section in the middle of patterns`() {
    val variants =
      listOf(
        createVariant("variant1", "stopA|stopB|stopC|stopD", "Stop A|Stop B|Stop C|Stop D"),
        createVariant("variant2", "stopX|stopB|stopC|stopY", "Stop X|Stop B|Stop C|Stop Y"),
        createVariant("variant3", "stopB|stopC", "Stop B|Stop C"),
      )

    val result = service.detectCommonSection(variants)

    assertNotNull(result)
    assertEquals("stopB|stopC", result!!.stopIds)
    assertEquals(listOf("Stop B", "Stop C"), result.stopNames)
  }

  @Test
  fun `should return null when no common continuous section exists`() {
    val variants =
      listOf(
        createVariant("variant1", "stop1|stop2|stop3", "Stop 1|Stop 2|Stop 3"),
        createVariant("variant2", "stop4|stop5|stop6", "Stop 4|Stop 5|Stop 6"),
      )

    val result = service.detectCommonSection(variants)

    assertNull(result)
  }

  @Test
  fun `should find common section when stops appear in different order`() {
    val variants =
      listOf(
        createVariant("variant1", "stopA|stopB|stopC|stopD|stopE", "A|B|C|D|E"),
        createVariant("variant2", "stopX|stopC|stopD|stopE|stopY", "X|C|D|E|Y"),
      )

    val result = service.detectCommonSection(variants)

    assertNotNull(result)
    assertEquals("stopC|stopD|stopE", result!!.stopIds)
    assertEquals(listOf("C", "D", "E"), result.stopNames)
  }

  @Test
  fun `should prefer longer common section over shorter one`() {
    val variants =
      listOf(
        createVariant("variant1", "stop1|stop2|stop3|stop4|stop5", "S1|S2|S3|S4|S5"),
        createVariant("variant2", "stop1|stop2|stop3|stop6", "S1|S2|S3|S6"),
        createVariant("variant3", "stop0|stop1|stop2|stop3|stop4", "S0|S1|S2|S3|S4"),
      )

    val result = service.detectCommonSection(variants)

    assertNotNull(result)
    // All three variants share stop1|stop2|stop3
    assertEquals("stop1|stop2|stop3", result!!.stopIds)
    assertEquals(listOf("S1", "S2", "S3"), result.stopNames)
  }

  @Test
  fun `should handle variants with single common stop`() {
    val variants =
      listOf(
        createVariant("variant1", "stop1|stop2", "Stop 1|Stop 2"),
        createVariant("variant2", "stop2|stop3", "Stop 2|Stop 3"),
        createVariant("variant3", "stop0|stop2", "Stop 0|Stop 2"),
      )

    val result = service.detectCommonSection(variants)

    assertNotNull(result)
    assertEquals("stop2", result!!.stopIds)
    assertEquals(listOf("Stop 2"), result.stopNames)
  }

  private fun createVariant(
    id: String,
    stopPattern: String,
    stopNamePattern: String
  ): RouteVariant =
    RouteVariant(
      id = VariantHash(id.padEnd(64, '0')),
      routeId = RouteId("r-test"),
      stopPattern = stopPattern,
      stopNamePattern = stopNamePattern,
      stopCount = stopPattern.split("|").size,
      firstStopId = stopPattern.split("|").first(),
      lastStopId = stopPattern.split("|").last(),
      directionId = 0,
      firstSeen = Instant.now(),
      lastSeen = Instant.now(),
    )
}
