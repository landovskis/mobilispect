package com.mobilispect.backend.route.handler

import com.mobilispect.backend.agency.AgencyId
import com.mobilispect.backend.feed.api.GTFSStop
import com.mobilispect.backend.feed.api.handler.GTFSDataBundle
import com.mobilispect.backend.feed.api.handler.GTFSDataType
import com.mobilispect.backend.feed.api.handler.ImportContext
import com.mobilispect.backend.feed.api.handler.ImportResult
import com.mobilispect.backend.feed.api.ids.FeedLocalAgencyId
import com.mobilispect.backend.feed.api.ids.GTFSStopId
import com.mobilispect.backend.feed.domain.model.ids.FeedId
import com.mobilispect.backend.feed.model.ids.ImportId
import com.mobilispect.backend.route.RouteId
import com.mobilispect.backend.route.domain.model.RouteVariant
import com.mobilispect.backend.route.domain.model.StopSpacing
import com.mobilispect.backend.route.domain.model.ids.VariantHash
import com.mobilispect.backend.route.domain.repository.RouteVariantRepository
import com.mobilispect.backend.route.domain.repository.StopSpacingRepository
import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import java.time.Instant
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class StopSpacingFeedDataHandlerTest {

  private lateinit var stopSpacingRepository: StopSpacingRepository
  private lateinit var routeVariantRepository: RouteVariantRepository
  private lateinit var handler: StopSpacingFeedDataHandler

  @BeforeEach
  fun setUp() {
    stopSpacingRepository = mockk()
    routeVariantRepository = mockk()
    handler = StopSpacingFeedDataHandler(stopSpacingRepository, routeVariantRepository)
  }

  @Test
  fun `dataTypes returns STOP`() {
    assertThat(handler.dataTypes()).containsExactly(GTFSDataType.STOP)
  }

  @Test
  fun `priority returns 3 after route variants`() {
    // Stop spacing should be processed after route variants (priority 4)
    assertThat(handler.priority()).isEqualTo(3)
  }

  @Test
  fun `handle returns success with zero when no variants exist`() {
    val feedId = FeedId("f-abc-test")
    val bundle = GTFSDataBundle(feedId = feedId, stops = createTestStops())
    val context = ImportContext(importId = ImportId.random(), startedAt = Instant.now())

    every { routeVariantRepository.findAll() } returns emptyList()

    val result = handler.handle(feedId, bundle, context)

    assertThat(result).isInstanceOf(ImportResult.Success::class.java)
    assertThat((result as ImportResult.Success).recordsProcessed).isEqualTo(0)
  }

  @Test
  fun `handle returns success with zero when bundle has no stops`() {
    val feedId = FeedId("f-abc-test")
    val bundle = GTFSDataBundle(feedId = feedId, stops = emptyList())
    val context = ImportContext(importId = ImportId.random(), startedAt = Instant.now())

    val result = handler.handle(feedId, bundle, context)

    assertThat(result).isInstanceOf(ImportResult.Success::class.java)
    assertThat((result as ImportResult.Success).recordsProcessed).isEqualTo(0)
    verify(exactly = 0) { routeVariantRepository.findAll() }
  }

  @Test
  fun `handle calculates and saves stop spacing for variant`() {
    val feedId = FeedId("f-abc-test")
    val stops = createTestStops()
    val bundle = GTFSDataBundle(feedId = feedId, stops = stops)
    val context = ImportContext(importId = ImportId.random(), startedAt = Instant.now())

    val variant = createTestVariant("stop1|stop2|stop3")

    every { routeVariantRepository.findAll() } returns listOf(variant)
    every { stopSpacingRepository.existsByVariant(any()) } returns false
    every { stopSpacingRepository.saveAll(any<List<StopSpacing>>()) } answers { firstArg() }

    val result = handler.handle(feedId, bundle, context)

    assertThat(result).isInstanceOf(ImportResult.Success::class.java)
    assertThat((result as ImportResult.Success).recordsProcessed).isEqualTo(1)

    verify(exactly = 1) { stopSpacingRepository.saveAll(any<List<StopSpacing>>()) }
  }

  @Test
  fun `handle deletes existing spacing records before saving new ones`() {
    val feedId = FeedId("f-abc-test")
    val stops = createTestStops()
    val bundle = GTFSDataBundle(feedId = feedId, stops = stops)
    val context = ImportContext(importId = ImportId.random(), startedAt = Instant.now())

    val variant = createTestVariant("stop1|stop2|stop3")

    every { routeVariantRepository.findAll() } returns listOf(variant)
    every { stopSpacingRepository.existsByVariant(variant.id.value) } returns true
    every { stopSpacingRepository.deleteByVariant(variant.id.value) } just Runs
    every { stopSpacingRepository.saveAll(any<List<StopSpacing>>()) } answers { firstArg() }

    handler.handle(feedId, bundle, context)

    verify(exactly = 1) { stopSpacingRepository.deleteByVariant(variant.id.value) }
    verify(exactly = 1) { stopSpacingRepository.saveAll(any<List<StopSpacing>>()) }
  }

  @Test
  fun `handle calculates correct number of spacing records`() {
    val feedId = FeedId("f-abc-test")
    val stops = createTestStops()
    val bundle = GTFSDataBundle(feedId = feedId, stops = stops)
    val context = ImportContext(importId = ImportId.random(), startedAt = Instant.now())

    // 3 stops = 2 spacing records (between consecutive pairs)
    val variant = createTestVariant("stop1|stop2|stop3")

    every { routeVariantRepository.findAll() } returns listOf(variant)
    every { stopSpacingRepository.existsByVariant(any()) } returns false

    val savedSpacings = slot<List<StopSpacing>>()
    every { stopSpacingRepository.saveAll(capture(savedSpacings)) } answers { firstArg() }

    handler.handle(feedId, bundle, context)

    assertThat(savedSpacings.captured).hasSize(2)
    assertThat(savedSpacings.captured[0].fromStopId).isEqualTo("stop1")
    assertThat(savedSpacings.captured[0].toStopId).isEqualTo("stop2")
    assertThat(savedSpacings.captured[0].stopSequence).isEqualTo(0)
    assertThat(savedSpacings.captured[1].fromStopId).isEqualTo("stop2")
    assertThat(savedSpacings.captured[1].toStopId).isEqualTo("stop3")
    assertThat(savedSpacings.captured[1].stopSequence).isEqualTo(1)
  }

  @Test
  fun `handle calculates distance using Haversine formula`() {
    val feedId = FeedId("f-abc-test")
    // NYC Penn Station to Times Square - approximately 800m
    val stops =
      listOf(
        GTFSStop(
          stopId = GTFSStopId("penn"),
          name = "Penn Station",
          latitude = 40.7506,
          longitude = -73.9935,
        ),
        GTFSStop(
          stopId = GTFSStopId("times"),
          name = "Times Square",
          latitude = 40.7580,
          longitude = -73.9855,
        ),
      )
    val bundle = GTFSDataBundle(feedId = feedId, stops = stops)
    val context = ImportContext(importId = ImportId.random(), startedAt = Instant.now())

    val variant = createTestVariant("penn|times")

    every { routeVariantRepository.findAll() } returns listOf(variant)
    every { stopSpacingRepository.existsByVariant(any()) } returns false

    val savedSpacings = slot<List<StopSpacing>>()
    every { stopSpacingRepository.saveAll(capture(savedSpacings)) } answers { firstArg() }

    handler.handle(feedId, bundle, context)

    assertThat(savedSpacings.captured).hasSize(1)
    // Distance should be approximately 800-1000m
    assertThat(savedSpacings.captured[0].distanceMeters).isBetween(800.0, 1000.0)
  }

  @Test
  fun `handle skips stop pairs with missing coordinates`() {
    val feedId = FeedId("f-abc-test")
    val stops =
      listOf(
        GTFSStop(
          stopId = GTFSStopId("stop1"),
          name = "Stop 1",
          latitude = 40.7506,
          longitude = -73.9935,
        ),
        GTFSStop(
          stopId = GTFSStopId("stop2"),
          name = "Stop 2",
          latitude = null, // Missing coordinates
          longitude = null,
        ),
        GTFSStop(
          stopId = GTFSStopId("stop3"),
          name = "Stop 3",
          latitude = 40.7580,
          longitude = -73.9855,
        ),
      )
    val bundle = GTFSDataBundle(feedId = feedId, stops = stops)
    val context = ImportContext(importId = ImportId.random(), startedAt = Instant.now())

    val variant = createTestVariant("stop1|stop2|stop3")

    every { routeVariantRepository.findAll() } returns listOf(variant)
    every { stopSpacingRepository.existsByVariant(any()) } returns false

    val savedSpacings = slot<List<StopSpacing>>()
    every { stopSpacingRepository.saveAll(capture(savedSpacings)) } answers { firstArg() }

    handler.handle(feedId, bundle, context)

    // Both pairs involve stop2 which has no coordinates, so no spacings saved
    assertThat(savedSpacings.captured).isEmpty()
  }

  @Test
  fun `handle skips variants with fewer than two stops`() {
    val feedId = FeedId("f-abc-test")
    val stops = createTestStops()
    val bundle = GTFSDataBundle(feedId = feedId, stops = stops)
    val context = ImportContext(importId = ImportId.random(), startedAt = Instant.now())

    val variant = createTestVariant("stop1") // Only one stop

    every { routeVariantRepository.findAll() } returns listOf(variant)

    val result = handler.handle(feedId, bundle, context)

    assertThat(result).isInstanceOf(ImportResult.Success::class.java)
    assertThat((result as ImportResult.Success).recordsProcessed).isEqualTo(0)
    verify(exactly = 0) { stopSpacingRepository.saveAll(any<List<StopSpacing>>()) }
  }

  @Test
  fun `handle processes multiple variants`() {
    val feedId = FeedId("f-abc-test")
    val stops = createTestStops()
    val bundle = GTFSDataBundle(feedId = feedId, stops = stops)
    val context = ImportContext(importId = ImportId.random(), startedAt = Instant.now())

    val variant1 = createTestVariant("stop1|stop2", "variant1")
    val variant2 = createTestVariant("stop2|stop3", "variant2")

    every { routeVariantRepository.findAll() } returns listOf(variant1, variant2)
    every { stopSpacingRepository.existsByVariant(any()) } returns false
    every { stopSpacingRepository.saveAll(any<List<StopSpacing>>()) } answers { firstArg() }

    val result = handler.handle(feedId, bundle, context)

    assertThat(result).isInstanceOf(ImportResult.Success::class.java)
    assertThat((result as ImportResult.Success).recordsProcessed).isEqualTo(2)
    verify(exactly = 2) { stopSpacingRepository.saveAll(any<List<StopSpacing>>()) }
  }

  @Test
  fun `handle returns partial success when some variants fail`() {
    val feedId = FeedId("f-abc-test")
    val stops = createTestStops()
    val bundle = GTFSDataBundle(feedId = feedId, stops = stops)
    val context = ImportContext(importId = ImportId.random(), startedAt = Instant.now())

    val variant1 = createTestVariant("stop1|stop2", "variant1")
    val variant2 = createTestVariant("stop2|stop3", "variant2")

    every { routeVariantRepository.findAll() } returns listOf(variant1, variant2)
    every { stopSpacingRepository.existsByVariant(any()) } returns false

    var saveCallCount = 0
    every { stopSpacingRepository.saveAll(any<List<StopSpacing>>()) } answers
      {
        saveCallCount++
        if (saveCallCount == 2) {
          throw RuntimeException("Database error")
        }
        firstArg()
      }

    val result = handler.handle(feedId, bundle, context)

    assertThat(result).isInstanceOf(ImportResult.PartialSuccess::class.java)
    val partialSuccess = result as ImportResult.PartialSuccess
    assertThat(partialSuccess.recordsProcessed).isEqualTo(1)
    assertThat(partialSuccess.errors).hasSize(1)
  }

  @Test
  fun `handle skips stops not found in bundle`() {
    val feedId = FeedId("f-abc-test")
    val stops =
      listOf(
        GTFSStop(
          stopId = GTFSStopId("stop1"),
          name = "Stop 1",
          latitude = 40.7506,
          longitude = -73.9935,
        )
        // stop2 is not in the bundle
      )
    val bundle = GTFSDataBundle(feedId = feedId, stops = stops)
    val context = ImportContext(importId = ImportId.random(), startedAt = Instant.now())

    val variant = createTestVariant("stop1|stop2")

    every { routeVariantRepository.findAll() } returns listOf(variant)
    every { stopSpacingRepository.existsByVariant(any()) } returns false

    val savedSpacings = slot<List<StopSpacing>>()
    every { stopSpacingRepository.saveAll(capture(savedSpacings)) } answers { firstArg() }

    handler.handle(feedId, bundle, context)

    // No spacings can be calculated since stop2 is missing
    assertThat(savedSpacings.captured).isEmpty()
  }

  private fun createTestStops(): List<GTFSStop> =
    listOf(
      GTFSStop(
        stopId = GTFSStopId("stop1"),
        name = "Stop 1",
        latitude = 40.7506,
        longitude = -73.9935,
      ),
      GTFSStop(
        stopId = GTFSStopId("stop2"),
        name = "Stop 2",
        latitude = 40.7550,
        longitude = -73.9900,
      ),
      GTFSStop(
        stopId = GTFSStopId("stop3"),
        name = "Stop 3",
        latitude = 40.7600,
        longitude = -73.9850,
      ),
    )

  private fun createTestVariant(
    stopPattern: String,
    idValue: String = "test-variant-hash",
  ): RouteVariant {
    val feedId = FeedId("f-abc-test")
    val agencyId = AgencyId(feedId, FeedLocalAgencyId("agency-1"))
    val routeId =
      RouteId(agencyId, com.mobilispect.backend.feed.api.ids.FeedLocalRouteId("route-1"))
    val stopIds = stopPattern.split("|")

    return RouteVariant(
      id = VariantHash(idValue),
      routeId = routeId,
      stopPattern = stopPattern,
      stopNamePattern = stopIds.joinToString("|") { "Name $it" },
      stopCount = stopIds.size,
      firstStopId = stopIds.first(),
      lastStopId = stopIds.last(),
      directionId = 0,
      headsign = "Test Headsign",
      active = true,
      firstSeen = Instant.now(),
      lastSeen = Instant.now(),
    )
  }
}
