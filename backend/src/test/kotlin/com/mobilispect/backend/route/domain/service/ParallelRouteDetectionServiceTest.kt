package com.mobilispect.backend.route.domain.service

import com.mobilispect.backend.route.RouteId
import com.mobilispect.backend.route.domain.model.RouteVariant
import com.mobilispect.backend.route.domain.model.RouteVariantWithStops
import com.mobilispect.backend.route.domain.model.StopWithLocation
import com.mobilispect.backend.route.domain.model.ids.VariantHash
import java.time.Instant
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class ParallelRouteDetectionServiceTest {

  private val service: ParallelRouteDetectionService = ParallelRouteDetectionServiceImpl()

  // Downtown Montreal reference points ~300 m apart
  private val stopA1 = StopWithLocation("stop-a1", 45.5088, -73.5540) // Peel & Ste-Catherine
  private val stopA2 = StopWithLocation("stop-a2", 45.5061, -73.5695) // Guy & Ste-Catherine
  private val stopA3 = StopWithLocation("stop-a3", 45.5030, -73.5870) // Atwater & Ste-Catherine

  // Stops offset ~80 m north (well within 200 m threshold)
  private val stopB1 = StopWithLocation("stop-b1", 45.5096, -73.5540) // ~89 m N of A1
  private val stopB2 = StopWithLocation("stop-b2", 45.5069, -73.5695) // ~89 m N of A2
  private val stopB3 = StopWithLocation("stop-b3", 45.5038, -73.5870) // ~89 m N of A3

  // Stops far away on the South Shore (~5 km south)
  private val stopC1 = StopWithLocation("stop-c1", 45.4600, -73.5540)
  private val stopC2 = StopWithLocation("stop-c2", 45.4570, -73.5695)
  private val stopC3 = StopWithLocation("stop-c3", 45.4540, -73.5870)

  @Test
  fun `returns empty when fewer than 2 variants provided`() {
    val variant = buildVariantWithStops("v-001", "route-A", listOf(stopA1, stopA2, stopA3))

    val result = service.detectParallelRoutes(listOf(variant), thresholdMeters = 300.0)

    assertThat(result).isEmpty()
  }

  @Test
  fun `returns empty when no variants provided`() {
    val result = service.detectParallelRoutes(emptyList(), thresholdMeters = 300.0)

    assertThat(result).isEmpty()
  }

  @Test
  fun `detects two variants on same corridor as parallel`() {
    val variantA = buildVariantWithStops("v-001", "route-A", listOf(stopA1, stopA2, stopA3))
    val variantB = buildVariantWithStops("v-002", "route-B", listOf(stopB1, stopB2, stopB3))

    val result = service.detectParallelRoutes(listOf(variantA, variantB), thresholdMeters = 200.0)

    assertThat(result).hasSize(1)
    val group = result.first()
    assertThat(group.routeIds).containsExactlyInAnyOrder(RouteId("route-A"), RouteId("route-B"))
    assertThat(group.variantIds).hasSize(2)
  }

  @Test
  fun `does not detect routes on different corridors as parallel`() {
    val variantA = buildVariantWithStops("v-001", "route-A", listOf(stopA1, stopA2, stopA3))
    val variantC = buildVariantWithStops("v-003", "route-C", listOf(stopC1, stopC2, stopC3))

    val result = service.detectParallelRoutes(listOf(variantA, variantC), thresholdMeters = 300.0)

    assertThat(result).isEmpty()
  }

  @Test
  fun `groups three parallel routes on same corridor into one group`() {
    val variantA = buildVariantWithStops("v-001", "route-A", listOf(stopA1, stopA2, stopA3))
    val variantB = buildVariantWithStops("v-002", "route-B", listOf(stopB1, stopB2, stopB3))
    // Third variant also within 200 m (same stops as A, different route)
    val variantD = buildVariantWithStops("v-004", "route-D", listOf(stopA1, stopB2, stopA3))

    val result =
      service.detectParallelRoutes(listOf(variantA, variantB, variantD), thresholdMeters = 200.0)

    assertThat(result).hasSize(1)
    assertThat(result.first().routeIds)
      .containsExactlyInAnyOrder(RouteId("route-A"), RouteId("route-B"), RouteId("route-D"))
  }

  @Test
  fun `reports average distance between parallel routes`() {
    val variantA = buildVariantWithStops("v-001", "route-A", listOf(stopA1, stopA2, stopA3))
    val variantB = buildVariantWithStops("v-002", "route-B", listOf(stopB1, stopB2, stopB3))

    val result = service.detectParallelRoutes(listOf(variantA, variantB), thresholdMeters = 200.0)

    assertThat(result).hasSize(1)
    // Stops are ~89 m apart; average distance should be positive and within threshold
    assertThat(result.first().averageDistanceMeters).isGreaterThan(0.0).isLessThan(200.0)
  }

  @Test
  fun `merged stop sequence contains stops from all parallel variants`() {
    val variantA = buildVariantWithStops("v-001", "route-A", listOf(stopA1, stopA2, stopA3))
    val variantB = buildVariantWithStops("v-002", "route-B", listOf(stopB1, stopB2, stopB3))

    val result = service.detectParallelRoutes(listOf(variantA, variantB), thresholdMeters = 200.0)

    assertThat(result).hasSize(1)
    val merged = result.first().mergedStopSequence
    // Must contain all unique stop IDs from both routes
    assertThat(merged).containsAll(listOf("stop-a1", "stop-a2", "stop-a3"))
    assertThat(merged).containsAll(listOf("stop-b1", "stop-b2", "stop-b3"))
  }

  @Test
  fun `handles variant with no stops gracefully`() {
    val variantA = buildVariantWithStops("v-001", "route-A", listOf(stopA1, stopA2, stopA3))
    val variantEmpty =
      RouteVariantWithStops(variant = buildVariant("v-999", "route-Z", emptyList()), stops = emptyList())

    val result =
      service.detectParallelRoutes(listOf(variantA, variantEmpty), thresholdMeters = 300.0)

    assertThat(result).isEmpty()
  }

  @Test
  fun `does not merge routes when distance exceeds threshold`() {
    val variantA = buildVariantWithStops("v-001", "route-A", listOf(stopA1, stopA2, stopA3))
    val variantB = buildVariantWithStops("v-002", "route-B", listOf(stopB1, stopB2, stopB3))

    // ~89 m apart; threshold of 50 m should exclude them
    val result = service.detectParallelRoutes(listOf(variantA, variantB), thresholdMeters = 50.0)

    assertThat(result).isEmpty()
  }

  @Test
  fun `returns multiple disjoint groups when two corridors are each parallel internally`() {
    val variantA = buildVariantWithStops("v-001", "route-A", listOf(stopA1, stopA2, stopA3))
    val variantB = buildVariantWithStops("v-002", "route-B", listOf(stopB1, stopB2, stopB3))
    // C corridor stops offset ~80 m from C reference
    val stopC1b = StopWithLocation("stop-c1b", 45.4608, -73.5540)
    val stopC2b = StopWithLocation("stop-c2b", 45.4578, -73.5695)
    val stopC3b = StopWithLocation("stop-c3b", 45.4548, -73.5870)
    val variantC = buildVariantWithStops("v-003", "route-C", listOf(stopC1, stopC2, stopC3))
    val variantCb = buildVariantWithStops("v-004", "route-D", listOf(stopC1b, stopC2b, stopC3b))

    val result =
      service.detectParallelRoutes(
        listOf(variantA, variantB, variantC, variantCb),
        thresholdMeters = 200.0,
      )

    assertThat(result).hasSize(2)
  }

  @Test
  fun `does not propose merge when frequency difference exceeds threshold`() {
    val variantA = buildVariantWithStops("v-001", "route-A", listOf(stopA1, stopA2, stopA3))
    val variantB = buildVariantWithStops("v-002", "route-B", listOf(stopB1, stopB2, stopB3))
    // A runs every 10 min, B runs every 30 min — difference is 20 min > threshold of 10 min
    val freqMap = mapOf(variantA.variant.id to 10.0, variantB.variant.id to 30.0)

    val result =
      service.detectParallelRoutes(
        listOf(variantA, variantB),
        thresholdMeters = 200.0,
        frequencyByVariant = freqMap,
        frequencyDifferenceThresholdMinutes = 10.0,
      )

    assertThat(result).isEmpty()
  }

  @Test
  fun `proposes merge when frequency difference is within threshold`() {
    val variantA = buildVariantWithStops("v-001", "route-A", listOf(stopA1, stopA2, stopA3))
    val variantB = buildVariantWithStops("v-002", "route-B", listOf(stopB1, stopB2, stopB3))
    // A runs every 10 min, B runs every 15 min — difference is 5 min <= threshold of 10 min
    val freqMap = mapOf(variantA.variant.id to 10.0, variantB.variant.id to 15.0)

    val result =
      service.detectParallelRoutes(
        listOf(variantA, variantB),
        thresholdMeters = 200.0,
        frequencyByVariant = freqMap,
        frequencyDifferenceThresholdMinutes = 10.0,
      )

    assertThat(result).hasSize(1)
    assertThat(result.first().routeIds)
      .containsExactlyInAnyOrder(RouteId("route-A"), RouteId("route-B"))
  }

  @Test
  fun `does not propose merge when one variant has no frequency data and threshold is set`() {
    val variantA = buildVariantWithStops("v-001", "route-A", listOf(stopA1, stopA2, stopA3))
    val variantB = buildVariantWithStops("v-002", "route-B", listOf(stopB1, stopB2, stopB3))
    // Only A has frequency data; B is missing
    val freqMap = mapOf(variantA.variant.id to 10.0)

    val result =
      service.detectParallelRoutes(
        listOf(variantA, variantB),
        thresholdMeters = 200.0,
        frequencyByVariant = freqMap,
        frequencyDifferenceThresholdMinutes = 10.0,
      )

    assertThat(result).isEmpty()
  }

  // ── helpers ──────────────────────────────────────────────────────────────────

  private fun buildVariantWithStops(
    id: String,
    routeId: String,
    stops: List<StopWithLocation>,
  ): RouteVariantWithStops = RouteVariantWithStops(buildVariant(id, routeId, stops), stops)

  private fun buildVariant(
    id: String,
    routeId: String,
    stops: List<StopWithLocation>,
  ): RouteVariant {
    // Produce a valid 64-character hex string: convert each character to its hex ordinal, pad to 64
    val hexId = id.map { "%02x".format(it.code) }.joinToString("").padEnd(64, '0').take(64)
    val stopPattern = stops.joinToString("|") { it.stopId }
    return RouteVariant(
      id = VariantHash(hexId),
      routeId = RouteId(routeId),
      stopPattern = stopPattern,
      stopCount = stops.size,
      firstStopId = stops.firstOrNull()?.stopId ?: "",
      lastStopId = stops.lastOrNull()?.stopId ?: "",
      firstSeen = Instant.EPOCH,
      lastSeen = Instant.EPOCH,
    )
  }
}
