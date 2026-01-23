package com.mobilispect.backend.route.handler

import com.mobilispect.backend.agency.AgencyId
import com.mobilispect.backend.feed.api.GTFSRoute
import com.mobilispect.backend.feed.api.handler.GTFSDataBundle
import com.mobilispect.backend.feed.api.handler.GTFSDataType
import com.mobilispect.backend.feed.api.handler.ImportContext
import com.mobilispect.backend.feed.api.handler.ImportResult
import com.mobilispect.backend.feed.api.ids.FeedLocalAgencyId
import com.mobilispect.backend.feed.api.ids.FeedLocalRouteId
import com.mobilispect.backend.feed.domain.model.ids.FeedId
import com.mobilispect.backend.feed.model.ids.ImportId
import com.mobilispect.backend.route.RouteId
import com.mobilispect.backend.route.domain.model.Route
import com.mobilispect.backend.route.domain.model.RouteType
import com.mobilispect.backend.route.domain.repository.RouteRepository
import com.mobilispect.backend.route.events.RouteImported
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
import org.springframework.context.ApplicationEventPublisher

class RouteFeedDataHandlerTest {

  private lateinit var routeRepository: RouteRepository
  private lateinit var eventPublisher: ApplicationEventPublisher
  private lateinit var handler: RouteFeedDataHandler

  @BeforeEach
  fun setUp() {
    routeRepository = mockk()
    eventPublisher = mockk()
    handler = RouteFeedDataHandler(routeRepository, eventPublisher)
  }

  @Test
  fun `dataTypes returns ROUTE`() {
    assertThat(handler.dataTypes()).containsExactly(GTFSDataType.ROUTE)
  }

  @Test
  fun `priority returns 5 after agencies`() {
    // Routes should be processed after agencies (priority 10)
    assertThat(handler.priority()).isEqualTo(5)
  }

  @Test
  fun `handle saves routes from bundle`() {
    val feedId = FeedId("f-abc-test")
    val gtfsRoute =
      GTFSRoute(
        routeId = FeedLocalRouteId("route-1"),
        agencyId = FeedLocalAgencyId("agency-1"),
        shortName = "1",
        longName = "Main Street",
        type = 3, // Bus
      )
    val bundle = GTFSDataBundle(feedId = feedId, routes = listOf(gtfsRoute))
    val context = ImportContext(importId = ImportId.random(), startedAt = Instant.now())

    val savedRoute = slot<Route>()
    every { routeRepository.findById(any()) } returns null
    every { routeRepository.save(capture(savedRoute)) } answers { savedRoute.captured }
    every { eventPublisher.publishEvent(any<RouteImported>()) } just Runs

    val result = handler.handle(feedId, bundle, context)

    assertThat(result).isInstanceOf(ImportResult.Success::class.java)
    assertThat((result as ImportResult.Success).recordsProcessed).isEqualTo(1)

    verify(exactly = 1) { routeRepository.save(any()) }
    verify(exactly = 1) { eventPublisher.publishEvent(any<RouteImported>()) }

    assertThat(savedRoute.captured.gtfsRouteId).isEqualTo("route-1")
    assertThat(savedRoute.captured.shortName).isEqualTo("1")
    assertThat(savedRoute.captured.longName).isEqualTo("Main Street")
    assertThat(savedRoute.captured.routeType).isEqualTo(RouteType.BUS)
    assertThat(savedRoute.captured.active).isTrue()
  }

  @Test
  fun `handle processes multiple routes`() {
    val feedId = FeedId("f-abc-test")
    val routes =
      listOf(
        GTFSRoute(
          routeId = FeedLocalRouteId("route-1"),
          agencyId = FeedLocalAgencyId("agency-1"),
          shortName = "1",
          longName = "First Avenue",
          type = 3,
        ),
        GTFSRoute(
          routeId = FeedLocalRouteId("route-2"),
          agencyId = FeedLocalAgencyId("agency-1"),
          shortName = "2",
          longName = "Second Avenue",
          type = 0, // Tram
        ),
      )
    val bundle = GTFSDataBundle(feedId = feedId, routes = routes)
    val context = ImportContext(importId = ImportId.random(), startedAt = Instant.now())

    every { routeRepository.findById(any()) } returns null
    every { routeRepository.save(any()) } answers { firstArg() }
    every { eventPublisher.publishEvent(any<RouteImported>()) } just Runs

    val result = handler.handle(feedId, bundle, context)

    assertThat(result).isInstanceOf(ImportResult.Success::class.java)
    assertThat((result as ImportResult.Success).recordsProcessed).isEqualTo(2)
    verify(exactly = 2) { routeRepository.save(any()) }
    verify(exactly = 2) { eventPublisher.publishEvent(any<RouteImported>()) }
  }

  @Test
  fun `handle returns success with zero when bundle has no routes`() {
    val feedId = FeedId("f-abc-test")
    val bundle = GTFSDataBundle(feedId = feedId, routes = emptyList())
    val context = ImportContext(importId = ImportId.random(), startedAt = Instant.now())

    val result = handler.handle(feedId, bundle, context)

    assertThat(result).isInstanceOf(ImportResult.Success::class.java)
    assertThat((result as ImportResult.Success).recordsProcessed).isEqualTo(0)
    verify(exactly = 0) { routeRepository.save(any()) }
  }

  @Test
  fun `handle updates existing route instead of creating new`() {
    val feedId = FeedId("f-abc-test")
    val agencyId = AgencyId(feedId, FeedLocalAgencyId("agency-1"))
    val routeId = RouteId(agencyId, FeedLocalRouteId("route-1"))

    val existingRoute =
      Route(
        id = routeId,
        agencyId = agencyId,
        gtfsRouteId = "route-1",
        shortName = "OLD",
        longName = "Old Name",
        routeType = RouteType.BUS,
        active = false,
      )

    val gtfsRoute =
      GTFSRoute(
        routeId = FeedLocalRouteId("route-1"),
        agencyId = FeedLocalAgencyId("agency-1"),
        shortName = "NEW",
        longName = "New Name",
        type = 3,
      )
    val bundle = GTFSDataBundle(feedId = feedId, routes = listOf(gtfsRoute))
    val context = ImportContext(importId = ImportId.random(), startedAt = Instant.now())

    val savedRoute = slot<Route>()
    every { routeRepository.findById(routeId) } returns existingRoute
    every { routeRepository.save(capture(savedRoute)) } answers { savedRoute.captured }
    every { eventPublisher.publishEvent(any<RouteImported>()) } just Runs

    val result = handler.handle(feedId, bundle, context)

    assertThat(result).isInstanceOf(ImportResult.Success::class.java)

    // Should update with new values
    assertThat(savedRoute.captured.shortName).isEqualTo("NEW")
    assertThat(savedRoute.captured.longName).isEqualTo("New Name")
    assertThat(savedRoute.captured.active).isTrue() // Reactivated
  }

  @Test
  fun `handle uses default agency when route has no agencyId`() {
    val feedId = FeedId("f-abc-test")
    val gtfsRoute =
      GTFSRoute(
        routeId = FeedLocalRouteId("route-1"),
        agencyId = null, // No agency specified
        shortName = "1",
        longName = "Main Street",
        type = 3,
      )
    val bundle = GTFSDataBundle(feedId = feedId, routes = listOf(gtfsRoute))
    val context = ImportContext(importId = ImportId.random(), startedAt = Instant.now())

    val savedRoute = slot<Route>()
    every { routeRepository.findById(any()) } returns null
    every { routeRepository.save(capture(savedRoute)) } answers { savedRoute.captured }
    every { eventPublisher.publishEvent(any<RouteImported>()) } just Runs

    handler.handle(feedId, bundle, context)

    // Should use default agency
    assertThat(savedRoute.captured.agencyId.value).contains("default-agency")
  }

  @Test
  fun `handle uses shortName or routeId when longName is null`() {
    val feedId = FeedId("f-abc-test")
    val gtfsRoute =
      GTFSRoute(
        routeId = FeedLocalRouteId("route-1"),
        agencyId = FeedLocalAgencyId("agency-1"),
        shortName = "Express",
        longName = null,
        type = 3,
      )
    val bundle = GTFSDataBundle(feedId = feedId, routes = listOf(gtfsRoute))
    val context = ImportContext(importId = ImportId.random(), startedAt = Instant.now())

    val savedRoute = slot<Route>()
    every { routeRepository.findById(any()) } returns null
    every { routeRepository.save(capture(savedRoute)) } answers { savedRoute.captured }
    every { eventPublisher.publishEvent(any<RouteImported>()) } just Runs

    handler.handle(feedId, bundle, context)

    assertThat(savedRoute.captured.longName).isEqualTo("Express")
  }

  @Test
  fun `handle publishes RouteImported event with correct data`() {
    val feedId = FeedId("f-abc-test")
    val gtfsRoute =
      GTFSRoute(
        routeId = FeedLocalRouteId("route-1"),
        agencyId = FeedLocalAgencyId("agency-1"),
        shortName = "1",
        longName = "Main Street",
        type = 3,
      )
    val bundle = GTFSDataBundle(feedId = feedId, routes = listOf(gtfsRoute))
    val context = ImportContext(importId = ImportId.random(), startedAt = Instant.now())

    val publishedEvent = slot<RouteImported>()
    every { routeRepository.findById(any()) } returns null
    every { routeRepository.save(any()) } answers { firstArg() }
    every { eventPublisher.publishEvent(capture(publishedEvent)) } just Runs

    handler.handle(feedId, bundle, context)

    assertThat(publishedEvent.captured.gtfsRouteId).isEqualTo("route-1")
  }

  @Test
  fun `handle returns partial success when some routes fail to save`() {
    val feedId = FeedId("f-abc-test")
    val routes =
      listOf(
        GTFSRoute(
          routeId = FeedLocalRouteId("route-1"),
          agencyId = FeedLocalAgencyId("agency-1"),
          shortName = "1",
          longName = "First",
          type = 3,
        ),
        GTFSRoute(
          routeId = FeedLocalRouteId("route-2"),
          agencyId = FeedLocalAgencyId("agency-1"),
          shortName = "2",
          longName = "Second",
          type = 3,
        ),
      )
    val bundle = GTFSDataBundle(feedId = feedId, routes = routes)
    val context = ImportContext(importId = ImportId.random(), startedAt = Instant.now())

    var callCount = 0
    every { routeRepository.findById(any()) } returns null
    every { routeRepository.save(any()) } answers
      {
        callCount++
        if (callCount == 2) {
          throw RuntimeException("Database error")
        }
        firstArg()
      }
    every { eventPublisher.publishEvent(any<RouteImported>()) } just Runs

    val result = handler.handle(feedId, bundle, context)

    assertThat(result).isInstanceOf(ImportResult.PartialSuccess::class.java)
    val partialSuccess = result as ImportResult.PartialSuccess
    assertThat(partialSuccess.recordsProcessed).isEqualTo(1)
    assertThat(partialSuccess.errors).hasSize(1)
    assertThat(partialSuccess.errors.first().recordId).isEqualTo("route-2")
  }

  @Test
  fun `handle defaults to BUS type when gtfs type is null`() {
    val feedId = FeedId("f-abc-test")
    val gtfsRoute =
      GTFSRoute(
        routeId = FeedLocalRouteId("route-1"),
        agencyId = FeedLocalAgencyId("agency-1"),
        shortName = "1",
        longName = "Main Street",
        type = null, // Unknown type
      )
    val bundle = GTFSDataBundle(feedId = feedId, routes = listOf(gtfsRoute))
    val context = ImportContext(importId = ImportId.random(), startedAt = Instant.now())

    val savedRoute = slot<Route>()
    every { routeRepository.findById(any()) } returns null
    every { routeRepository.save(capture(savedRoute)) } answers { savedRoute.captured }
    every { eventPublisher.publishEvent(any<RouteImported>()) } just Runs

    handler.handle(feedId, bundle, context)

    assertThat(savedRoute.captured.routeType).isEqualTo(RouteType.BUS)
  }
}
