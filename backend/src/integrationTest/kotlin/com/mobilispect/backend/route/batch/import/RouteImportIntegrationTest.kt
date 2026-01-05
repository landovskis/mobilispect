package com.mobilispect.backend.route.batch.import

import com.mobilispect.backend.agency.AgencyId
import com.mobilispect.backend.agency.domain.model.Agency
import com.mobilispect.backend.agency.domain.repository.AgencyRepository
import com.mobilispect.backend.feed.api.GTFSRoute
import com.mobilispect.backend.feed.api.ids.FeedLocalAgencyId
import com.mobilispect.backend.feed.api.ids.FeedLocalRouteId
import com.mobilispect.backend.feed.domain.model.ids.FeedId
import com.mobilispect.backend.route.RouteId
import com.mobilispect.backend.route.domain.model.Route
import com.mobilispect.backend.route.domain.model.RouteType
import com.mobilispect.backend.route.domain.repository.RouteRepository
import com.mobilispect.backend.route.events.RouteImported
import java.time.Instant
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.batch.infrastructure.item.Chunk
import org.springframework.batch.test.context.SpringBatchTest
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.boot.testcontainers.service.connection.ServiceConnection
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Component
import org.springframework.test.context.TestPropertySource
import org.springframework.transaction.annotation.Transactional
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers

/**
 * Integration test for Route Import batch processing.
 *
 * Tests the route import batch components (RouteProcessor and RouteWriter) using Testcontainers for
 * PostgreSQL.
 *
 * Constitutional Requirements:
 * - Test-Driven Quality: Integration tests using Testcontainers
 * - Module boundaries: Tests through public APIs only
 * - Event-driven architecture: Verifies RouteImported events
 */
@SpringBootTest
@SpringBatchTest
@Transactional
@Testcontainers
@TestPropertySource(properties = ["spring.batch.job.enabled=false"])
@Import(RouteImportIntegrationTest.TestConfig::class)
class RouteImportIntegrationTest {

  companion object {
    @Container
    @ServiceConnection
    @JvmStatic
    val postgres: PostgreSQLContainer<*> =
      PostgreSQLContainer("postgres:18-alpine")
        .withDatabaseName("mobilispect_test")
        .withUsername("test")
        .withPassword("test")
  }

  @Autowired private lateinit var routeRepository: RouteRepository

  @Autowired private lateinit var agencyRepository: AgencyRepository

  @Autowired private lateinit var routeProcessor: RouteProcessor

  @Autowired private lateinit var routeWriter: RouteWriter

  @Autowired private lateinit var eventListener: TestEventListener

  @Autowired
  private lateinit var feedRepository: com.mobilispect.backend.feed.repository.FeedRepository

  private val fixedInstant = Instant.parse("2025-01-15T12:00:00Z")
  private val feedOnestopId = "f-test-feed"
  private val agencyGtfsId = "CITPI"

  @BeforeEach
  fun setUp() {
    // Clean up before each test - delete all routes, agencies, and feeds
    routeRepository.findAll().forEach { routeRepository.deleteById(it.id) }
    agencyRepository.findAll().forEach { agencyRepository.deleteById(it.agencyId) }
    feedRepository.deleteAll()
    eventListener.clear()

    // Create test feed
    val feed =
      com.mobilispect.backend.feed.model.FeedEntity(
        feedId = feedOnestopId,
        regions = mutableSetOf(),
        name = "Test Feed",
        downloadUrl = "https://example.com/test-feed.zip",
        specType = com.mobilispect.backend.feed.model.FeedSpecType.GTFS,
        status = com.mobilispect.backend.feed.model.FeedStatus.ACTIVE,
        createdAt = fixedInstant,
        updatedAt = fixedInstant,
      )
    feedRepository.save(feed)

    // Create test agency
    val agency =
      Agency(
        agencyId = AgencyId("o-$feedOnestopId-$agencyGtfsId"),
        feedId = FeedId(feedOnestopId),
        gtfsAgencyId = FeedLocalAgencyId(agencyGtfsId),
        name = "Test Transit Agency",
        website = "https://example.com",
        active = true,
      )
    agencyRepository.save(agency)
  }

  @Test
  fun `should successfully process and write new routes from GTFS data`() {
    // Given: Parsed routes to process
    val routeInput1 =
      RouteInput(
        parsedRoute =
          GTFSRoute(
            routeId = FeedLocalRouteId("1"),
            agencyId = FeedLocalAgencyId(agencyGtfsId),
            shortName = "1",
            longName = "Gare Vaudreuil/Parc Industriel",
            type = 3, // Bus
          ),
        feedOnestopId = feedOnestopId,
      )

    val routeInput2 =
      RouteInput(
        parsedRoute =
          GTFSRoute(
            routeId = FeedLocalRouteId("T1"),
            agencyId = FeedLocalAgencyId(agencyGtfsId),
            shortName = "T1",
            longName = "Express Route",
            type = 3,
          ),
        feedOnestopId = feedOnestopId,
      )

    // When: Process route inputs
    val routeBatch1 = routeProcessor.process(routeInput1)
    val routeBatch2 = routeProcessor.process(routeInput2)

    // Write processed routes
    val chunk = Chunk(listOf(routeBatch1, routeBatch2))
    routeWriter.write(chunk)

    // Then: Verify routes were created in database
    val routes = routeRepository.findAll()
    assertThat(routes).hasSize(2)

    // Verify first route
    val route1 = routes.find { it.gtfsRouteId == FeedLocalRouteId("1") }
    assertThat(route1).isNotNull
    assertThat(route1!!.id.value).isEqualTo("r-test-feed-1")
    assertThat(route1.agencyId.value).isEqualTo("o-$feedOnestopId-$agencyGtfsId")
    assertThat(route1.shortName).isEqualTo("1")
    assertThat(route1.longName).isEqualTo("Gare Vaudreuil/Parc Industriel")
    assertThat(route1.routeType).isEqualTo(RouteType.BUS)
    assertThat(route1.active).isTrue()

    // Verify second route
    val route2 = routes.find { it.gtfsRouteId == FeedLocalRouteId("T1") }
    assertThat(route2).isNotNull
    assertThat(route2!!.id.value).isEqualTo("r-test-feed-T1")
    assertThat(route2.shortName).isEqualTo("T1")
    assertThat(route2.longName).isEqualTo("Express Route")

    // Verify RouteImported events were published
    assertThat(eventListener.routeImportedEvents).hasSize(2)
    assertThat(eventListener.routeImportedEvents.map { it.gtfsRouteId })
      .containsExactlyInAnyOrder(FeedLocalRouteId("1"), FeedLocalRouteId("T1"))
  }

  @Test
  fun `should update existing routes when importing same route again`() {
    // Given: Existing route in database
    val existingRoute =
      Route(
        id = RouteId("r-test-feed-1"),
        agencyId = AgencyId("o-$feedOnestopId-$agencyGtfsId"),
        gtfsRouteId = FeedLocalRouteId("1"),
        shortName = "1",
        longName = "Old Name",
        routeType = RouteType.BUS,
        color = "FF0000",
        textColor = "FFFFFF",
        active = false, // Inactive
      )
    routeRepository.save(existingRoute)

    // And: Updated route input
    val routeInput =
      RouteInput(
        parsedRoute =
          GTFSRoute(
            routeId = FeedLocalRouteId("1"),
            agencyId = FeedLocalAgencyId(agencyGtfsId),
            shortName = "1",
            longName = "New Updated Name",
            type = 3,
          ),
        feedOnestopId = feedOnestopId,
      )

    // When: Process and write updated route
    val routeBatch = routeProcessor.process(routeInput)
    val chunk = Chunk(listOf(routeBatch))
    routeWriter.write(chunk)

    // Then: Verify route was updated
    val routes = routeRepository.findAll()
    assertThat(routes).hasSize(1)

    val updatedRoute = routes.first()
    assertThat(updatedRoute.id.value).isEqualTo("r-test-feed-1")
    assertThat(updatedRoute.longName).isEqualTo("New Updated Name")
    assertThat(updatedRoute.active).isTrue() // Should be reactivated
    assertThat(updatedRoute.routeType).isEqualTo(RouteType.BUS)

    // Verify RouteImported event was published
    assertThat(eventListener.routeImportedEvents).hasSize(1)
    assertThat(eventListener.routeImportedEvents.first().gtfsRouteId)
      .isEqualTo(FeedLocalRouteId("1"))
  }

  @Test
  fun `should handle routes with missing agency ID by using default`() {
    // Given: Create default agency
    val defaultAgency =
      Agency(
        agencyId = AgencyId("o-$feedOnestopId-default-agency"),
        feedId = FeedId(feedOnestopId),
        gtfsAgencyId = FeedLocalAgencyId("default-agency"),
        name = "Default Agency",
        website = "https://example.com",
        active = true,
      )
    agencyRepository.save(defaultAgency)

    // And: Route input with no agency ID
    val routeInput =
      RouteInput(
        parsedRoute =
          GTFSRoute(
            routeId = FeedLocalRouteId("R1"),
            agencyId = null, // No agency ID
            shortName = "R1",
            longName = "Route without Agency",
            type = 3,
          ),
        feedOnestopId = feedOnestopId,
      )

    // When: Process and write route
    val routeBatch = routeProcessor.process(routeInput)
    val chunk = Chunk(listOf(routeBatch))
    routeWriter.write(chunk)

    // Then: Verify route was created with default agency
    val routes = routeRepository.findAll()
    assertThat(routes).hasSize(1)

    val route = routes.first()
    assertThat(route.agencyId.value).isEqualTo("o-$feedOnestopId-default-agency")
    assertThat(route.gtfsRouteId).isEqualTo(FeedLocalRouteId("R1"))
  }

  @Test
  fun `should handle route with missing short name and long name`() {
    // Given: Route input with missing short and long names
    val routeInput =
      RouteInput(
        parsedRoute =
          GTFSRoute(
            routeId = FeedLocalRouteId("ROUTE_1"),
            agencyId = FeedLocalAgencyId(agencyGtfsId),
            shortName = null,
            longName = null,
            type = 3,
          ),
        feedOnestopId = feedOnestopId,
      )

    // When: Process and write route
    val routeBatch = routeProcessor.process(routeInput)
    val chunk = Chunk(listOf(routeBatch))
    routeWriter.write(chunk)

    // Then: Verify route was created with route ID as long name
    val routes = routeRepository.findAll()
    assertThat(routes).hasSize(1)

    val route = routes.first()
    assertThat(route.shortName).isNull()
    assertThat(route.longName).isEqualTo("ROUTE_1") // Falls back to route ID
    assertThat(route.gtfsRouteId).isEqualTo(FeedLocalRouteId("ROUTE_1"))
  }

  @Test
  fun `should correctly convert different GTFS route types`() {
    // Given: Route inputs with various route types
    val routeInputs =
      listOf(
        RouteInput(
          GTFSRoute(FeedLocalRouteId("R_TRAM"), FeedLocalAgencyId(agencyGtfsId), "T", "Tram", 0),
          feedOnestopId,
        ),
        RouteInput(
          GTFSRoute(
            FeedLocalRouteId("R_SUBWAY"),
            FeedLocalAgencyId(agencyGtfsId),
            "S",
            "Subway",
            1,
          ),
          feedOnestopId,
        ),
        RouteInput(
          GTFSRoute(FeedLocalRouteId("R_RAIL"), FeedLocalAgencyId(agencyGtfsId), "R", "Rail", 2),
          feedOnestopId,
        ),
        RouteInput(
          GTFSRoute(FeedLocalRouteId("R_BUS"), FeedLocalAgencyId(agencyGtfsId), "B", "Bus", 3),
          feedOnestopId,
        ),
        RouteInput(
          GTFSRoute(FeedLocalRouteId("R_FERRY"), FeedLocalAgencyId(agencyGtfsId), "F", "Ferry", 4),
          feedOnestopId,
        ),
      )

    // When: Process and write routes
    val routeBatches = routeInputs.map { routeProcessor.process(it) }
    val chunk = Chunk(routeBatches)
    routeWriter.write(chunk)

    // Then: Verify all routes have correct types
    val routes = routeRepository.findAll()
    assertThat(routes).hasSize(5)

    assertThat(routes.find { it.gtfsRouteId == FeedLocalRouteId("R_TRAM") }?.routeType)
      .isEqualTo(RouteType.TRAM)
    assertThat(routes.find { it.gtfsRouteId == FeedLocalRouteId("R_SUBWAY") }?.routeType)
      .isEqualTo(RouteType.SUBWAY)
    assertThat(routes.find { it.gtfsRouteId == FeedLocalRouteId("R_RAIL") }?.routeType)
      .isEqualTo(RouteType.RAIL)
    assertThat(routes.find { it.gtfsRouteId == FeedLocalRouteId("R_BUS") }?.routeType)
      .isEqualTo(RouteType.BUS)
    assertThat(routes.find { it.gtfsRouteId == FeedLocalRouteId("R_FERRY") }?.routeType)
      .isEqualTo(RouteType.FERRY)
  }

  @Test
  fun `should throw exception when agency not found`() {
    // Given: Route input with non-existent agency
    val routeInput =
      RouteInput(
        parsedRoute =
          GTFSRoute(
            routeId = FeedLocalRouteId("R1"),
            agencyId = FeedLocalAgencyId("NON_EXISTENT_AGENCY"),
            shortName = "R1",
            longName = "Route with Missing Agency",
            type = 3,
          ),
        feedOnestopId = feedOnestopId,
      )

    // When/Then: Processing should throw IllegalStateException
    val exception = assertThrows<IllegalStateException> { routeProcessor.process(routeInput) }

    assertThat(exception.message).contains("Agency not found")
    assertThat(exception.message).contains("NON_EXISTENT_AGENCY")
  }

  /** Test configuration for capturing domain events. */
  @TestConfiguration
  class TestConfig {
    @Bean fun testEventListener() = TestEventListener()
  }

  /** Test event listener to capture RouteImported events. */
  @Component
  class TestEventListener {
    val routeImportedEvents = mutableListOf<RouteImported>()

    @EventListener
    fun onRouteImported(event: RouteImported) {
      routeImportedEvents.add(event)
    }

    fun clear() {
      routeImportedEvents.clear()
    }
  }
}
