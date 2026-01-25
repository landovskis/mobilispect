package com.mobilispect.backend.route.handler

import com.mobilispect.backend.agency.AgencyId
import com.mobilispect.backend.feed.api.handler.GTFSDataBundle
import com.mobilispect.backend.feed.api.handler.GTFSDataType
import com.mobilispect.backend.feed.api.handler.ImportContext
import com.mobilispect.backend.feed.api.handler.ImportResult
import com.mobilispect.backend.feed.api.ids.FeedLocalAgencyId
import com.mobilispect.backend.feed.api.ids.FeedLocalRouteId
import com.mobilispect.backend.feed.domain.model.ids.FeedId
import com.mobilispect.backend.feed.model.ids.ImportId
import com.mobilispect.backend.route.RouteId
import com.mobilispect.backend.route.domain.model.RouteClassification
import com.mobilispect.backend.route.domain.model.RouteVariant
import com.mobilispect.backend.route.domain.model.ids.VariantHash
import com.mobilispect.backend.route.domain.repository.RouteVariantRepository
import com.mobilispect.backend.route.domain.repository.StopSpacingRepository
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import java.time.Instant
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class RouteClassificationFeedDataHandlerTest {

  private lateinit var stopSpacingRepository: StopSpacingRepository
  private lateinit var routeVariantRepository: RouteVariantRepository
  private lateinit var handler: RouteClassificationFeedDataHandler

  @BeforeEach
  fun setUp() {
    stopSpacingRepository = mockk()
    routeVariantRepository = mockk()
    handler = RouteClassificationFeedDataHandler(stopSpacingRepository, routeVariantRepository)
  }

  @Test
  fun `dataTypes returns STOP`() {
    assertThat(handler.dataTypes()).containsExactly(GTFSDataType.STOP)
  }

  @Test
  fun `priority returns 2 after stop spacing handler`() {
    // Classification should be processed after stop spacing (priority 3)
    assertThat(handler.priority()).isEqualTo(2)
  }

  @Test
  fun `handle returns success with zero when no variants exist`() {
    val feedId = FeedId("f-abc-test")
    val bundle = GTFSDataBundle(feedId = feedId)
    val context = ImportContext(importId = ImportId.random(), startedAt = Instant.now())

    every { routeVariantRepository.findAll() } returns emptyList()

    val result = handler.handle(feedId, bundle, context)

    assertThat(result).isInstanceOf(ImportResult.Success::class.java)
    assertThat((result as ImportResult.Success).recordsProcessed).isEqualTo(0)
  }

  @Test
  fun `handle classifies variant as LOCAL when average spacing below 400m`() {
    val feedId = FeedId("f-abc-test")
    val bundle = GTFSDataBundle(feedId = feedId)
    val context = ImportContext(importId = ImportId.random(), startedAt = Instant.now())

    val variant = createTestVariant("variant1")

    every { routeVariantRepository.findAll() } returns listOf(variant)
    every { stopSpacingRepository.calculateAverageByVariant(variant.id.value) } returns 350.0

    val savedVariant = slot<RouteVariant>()
    every { routeVariantRepository.save(capture(savedVariant)) } answers { firstArg() }

    val result = handler.handle(feedId, bundle, context)

    assertThat(result).isInstanceOf(ImportResult.Success::class.java)
    assertThat((result as ImportResult.Success).recordsProcessed).isEqualTo(1)
    assertThat(savedVariant.captured.classification).isEqualTo(RouteClassification.LOCAL)
    assertThat(savedVariant.captured.averageStopSpacingMeters).isEqualTo(350.0)
  }

  @Test
  fun `handle classifies variant as LIMITED when average spacing between 400m and 800m`() {
    val feedId = FeedId("f-abc-test")
    val bundle = GTFSDataBundle(feedId = feedId)
    val context = ImportContext(importId = ImportId.random(), startedAt = Instant.now())

    val variant = createTestVariant("variant1")

    every { routeVariantRepository.findAll() } returns listOf(variant)
    every { stopSpacingRepository.calculateAverageByVariant(variant.id.value) } returns 600.0

    val savedVariant = slot<RouteVariant>()
    every { routeVariantRepository.save(capture(savedVariant)) } answers { firstArg() }

    handler.handle(feedId, bundle, context)

    assertThat(savedVariant.captured.classification).isEqualTo(RouteClassification.LIMITED)
    assertThat(savedVariant.captured.averageStopSpacingMeters).isEqualTo(600.0)
  }

  @Test
  fun `handle classifies variant as RAPID when average spacing between 800m and 1500m`() {
    val feedId = FeedId("f-abc-test")
    val bundle = GTFSDataBundle(feedId = feedId)
    val context = ImportContext(importId = ImportId.random(), startedAt = Instant.now())

    val variant = createTestVariant("variant1")

    every { routeVariantRepository.findAll() } returns listOf(variant)
    every { stopSpacingRepository.calculateAverageByVariant(variant.id.value) } returns 1200.0

    val savedVariant = slot<RouteVariant>()
    every { routeVariantRepository.save(capture(savedVariant)) } answers { firstArg() }

    handler.handle(feedId, bundle, context)

    assertThat(savedVariant.captured.classification).isEqualTo(RouteClassification.RAPID)
    assertThat(savedVariant.captured.averageStopSpacingMeters).isEqualTo(1200.0)
  }

  @Test
  fun `handle classifies variant as SUBURBAN when average spacing between 1500m and 3000m`() {
    val feedId = FeedId("f-abc-test")
    val bundle = GTFSDataBundle(feedId = feedId)
    val context = ImportContext(importId = ImportId.random(), startedAt = Instant.now())

    val variant = createTestVariant("variant1")

    every { routeVariantRepository.findAll() } returns listOf(variant)
    every { stopSpacingRepository.calculateAverageByVariant(variant.id.value) } returns 2500.0

    val savedVariant = slot<RouteVariant>()
    every { routeVariantRepository.save(capture(savedVariant)) } answers { firstArg() }

    handler.handle(feedId, bundle, context)

    assertThat(savedVariant.captured.classification).isEqualTo(RouteClassification.SUBURBAN)
    assertThat(savedVariant.captured.averageStopSpacingMeters).isEqualTo(2500.0)
  }

  @Test
  fun `handle classifies variant as REGIONAL when average spacing between 3000m and 5000m`() {
    val feedId = FeedId("f-abc-test")
    val bundle = GTFSDataBundle(feedId = feedId)
    val context = ImportContext(importId = ImportId.random(), startedAt = Instant.now())

    val variant = createTestVariant("variant1")

    every { routeVariantRepository.findAll() } returns listOf(variant)
    every { stopSpacingRepository.calculateAverageByVariant(variant.id.value) } returns 4000.0

    val savedVariant = slot<RouteVariant>()
    every { routeVariantRepository.save(capture(savedVariant)) } answers { firstArg() }

    handler.handle(feedId, bundle, context)

    assertThat(savedVariant.captured.classification).isEqualTo(RouteClassification.REGIONAL)
    assertThat(savedVariant.captured.averageStopSpacingMeters).isEqualTo(4000.0)
  }

  @Test
  fun `handle classifies variant as EXPRESS when average spacing between 5000m and 10000m`() {
    val feedId = FeedId("f-abc-test")
    val bundle = GTFSDataBundle(feedId = feedId)
    val context = ImportContext(importId = ImportId.random(), startedAt = Instant.now())

    val variant = createTestVariant("variant1")

    every { routeVariantRepository.findAll() } returns listOf(variant)
    every { stopSpacingRepository.calculateAverageByVariant(variant.id.value) } returns 7500.0

    val savedVariant = slot<RouteVariant>()
    every { routeVariantRepository.save(capture(savedVariant)) } answers { firstArg() }

    handler.handle(feedId, bundle, context)

    assertThat(savedVariant.captured.classification).isEqualTo(RouteClassification.EXPRESS)
    assertThat(savedVariant.captured.averageStopSpacingMeters).isEqualTo(7500.0)
  }

  @Test
  fun `handle classifies variant as REGIONAL_EXPRESS when average spacing above 10000m`() {
    val feedId = FeedId("f-abc-test")
    val bundle = GTFSDataBundle(feedId = feedId)
    val context = ImportContext(importId = ImportId.random(), startedAt = Instant.now())

    val variant = createTestVariant("variant1")

    every { routeVariantRepository.findAll() } returns listOf(variant)
    every { stopSpacingRepository.calculateAverageByVariant(variant.id.value) } returns 15000.0

    val savedVariant = slot<RouteVariant>()
    every { routeVariantRepository.save(capture(savedVariant)) } answers { firstArg() }

    handler.handle(feedId, bundle, context)

    assertThat(savedVariant.captured.classification).isEqualTo(RouteClassification.REGIONAL_EXPRESS)
    assertThat(savedVariant.captured.averageStopSpacingMeters).isEqualTo(15000.0)
  }

  @Test
  fun `handle classifies variant as UNKNOWN when no spacing data exists`() {
    val feedId = FeedId("f-abc-test")
    val bundle = GTFSDataBundle(feedId = feedId)
    val context = ImportContext(importId = ImportId.random(), startedAt = Instant.now())

    val variant = createTestVariant("variant1")

    every { routeVariantRepository.findAll() } returns listOf(variant)
    every { stopSpacingRepository.calculateAverageByVariant(variant.id.value) } returns null

    val savedVariant = slot<RouteVariant>()
    every { routeVariantRepository.save(capture(savedVariant)) } answers { firstArg() }

    handler.handle(feedId, bundle, context)

    assertThat(savedVariant.captured.classification).isEqualTo(RouteClassification.UNKNOWN)
    assertThat(savedVariant.captured.averageStopSpacingMeters).isNull()
  }

  @Test
  fun `handle processes multiple variants`() {
    val feedId = FeedId("f-abc-test")
    val bundle = GTFSDataBundle(feedId = feedId)
    val context = ImportContext(importId = ImportId.random(), startedAt = Instant.now())

    val variant1 = createTestVariant("variant1")
    val variant2 = createTestVariant("variant2")
    val variant3 = createTestVariant("variant3")

    every { routeVariantRepository.findAll() } returns listOf(variant1, variant2, variant3)
    every { stopSpacingRepository.calculateAverageByVariant(variant1.id.value) } returns 300.0
    every { stopSpacingRepository.calculateAverageByVariant(variant2.id.value) } returns 1000.0
    every { stopSpacingRepository.calculateAverageByVariant(variant3.id.value) } returns 5500.0

    val savedVariants = mutableListOf<RouteVariant>()
    every { routeVariantRepository.save(capture(savedVariants)) } answers { firstArg() }

    val result = handler.handle(feedId, bundle, context)

    assertThat(result).isInstanceOf(ImportResult.Success::class.java)
    assertThat((result as ImportResult.Success).recordsProcessed).isEqualTo(3)
    assertThat(savedVariants).hasSize(3)

    val classifications = savedVariants.map { it.classification }
    assertThat(classifications)
      .containsExactlyInAnyOrder(
        RouteClassification.LOCAL,
        RouteClassification.RAPID,
        RouteClassification.EXPRESS,
      )
  }

  @Test
  fun `handle skips variant when classification unchanged`() {
    val feedId = FeedId("f-abc-test")
    val bundle = GTFSDataBundle(feedId = feedId)
    val context = ImportContext(importId = ImportId.random(), startedAt = Instant.now())

    // Variant already has LOCAL classification with 350m average
    val variant =
      createTestVariant("variant1")
        .copy(classification = RouteClassification.LOCAL, averageStopSpacingMeters = 350.0)

    every { routeVariantRepository.findAll() } returns listOf(variant)
    every { stopSpacingRepository.calculateAverageByVariant(variant.id.value) } returns 350.0

    val result = handler.handle(feedId, bundle, context)

    // No save should happen since nothing changed
    verify(exactly = 0) { routeVariantRepository.save(any()) }
    assertThat(result).isInstanceOf(ImportResult.Success::class.java)
    assertThat((result as ImportResult.Success).recordsProcessed).isEqualTo(0)
  }

  @Test
  fun `handle updates variant when classification changes`() {
    val feedId = FeedId("f-abc-test")
    val bundle = GTFSDataBundle(feedId = feedId)
    val context = ImportContext(importId = ImportId.random(), startedAt = Instant.now())

    // Variant was LOCAL but now has different spacing
    val variant =
      createTestVariant("variant1")
        .copy(classification = RouteClassification.LOCAL, averageStopSpacingMeters = 350.0)

    every { routeVariantRepository.findAll() } returns listOf(variant)
    // New average puts it in RAPID category
    every { stopSpacingRepository.calculateAverageByVariant(variant.id.value) } returns 1000.0

    val savedVariant = slot<RouteVariant>()
    every { routeVariantRepository.save(capture(savedVariant)) } answers { firstArg() }

    val result = handler.handle(feedId, bundle, context)

    assertThat(result).isInstanceOf(ImportResult.Success::class.java)
    assertThat((result as ImportResult.Success).recordsProcessed).isEqualTo(1)
    assertThat(savedVariant.captured.classification).isEqualTo(RouteClassification.RAPID)
    assertThat(savedVariant.captured.averageStopSpacingMeters).isEqualTo(1000.0)
  }

  @Test
  fun `handle returns partial success when some variants fail`() {
    val feedId = FeedId("f-abc-test")
    val bundle = GTFSDataBundle(feedId = feedId)
    val context = ImportContext(importId = ImportId.random(), startedAt = Instant.now())

    val variant1 = createTestVariant("variant1")
    val variant2 = createTestVariant("variant2")

    every { routeVariantRepository.findAll() } returns listOf(variant1, variant2)
    every { stopSpacingRepository.calculateAverageByVariant(variant1.id.value) } returns 350.0
    every { stopSpacingRepository.calculateAverageByVariant(variant2.id.value) } throws
      RuntimeException("Database error")

    every { routeVariantRepository.save(any()) } answers { firstArg() }

    val result = handler.handle(feedId, bundle, context)

    assertThat(result).isInstanceOf(ImportResult.PartialSuccess::class.java)
    val partialSuccess = result as ImportResult.PartialSuccess
    assertThat(partialSuccess.recordsProcessed).isEqualTo(1)
    assertThat(partialSuccess.errors).hasSize(1)
    assertThat(partialSuccess.errors[0].recordId).isEqualTo(variant2.id.value)
  }

  private fun createTestVariant(idSuffix: String): RouteVariant {
    val feedId = FeedId("f-abc-test")
    val agencyId = AgencyId(feedId, FeedLocalAgencyId("agency-1"))
    val routeId = RouteId(agencyId, FeedLocalRouteId("route-1"))
    // Create valid 64-character hex hash - use hashCode to generate unique hex suffix
    val hexSuffix = idSuffix.hashCode().toUInt().toString(16).padStart(8, '0')
    val hash = hexSuffix.padStart(64, 'a')

    return RouteVariant(
      id = VariantHash(hash),
      routeId = routeId,
      stopPattern = "stop1|stop2|stop3",
      stopNamePattern = "Stop 1|Stop 2|Stop 3",
      stopCount = 3,
      firstStopId = "stop1",
      lastStopId = "stop3",
      directionId = 0,
      headsign = "Test Headsign",
      active = true,
      firstSeen = Instant.now(),
      lastSeen = Instant.now(),
    )
  }
}
