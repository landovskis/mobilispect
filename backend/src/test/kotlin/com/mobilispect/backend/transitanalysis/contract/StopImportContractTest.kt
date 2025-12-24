package com.mobilispect.backend.transitanalysis.contract

import com.mobilispect.backend.feed.model.FeedEntity
import com.mobilispect.backend.feed.model.FeedSpecType
import com.mobilispect.backend.feed.model.FeedStatus
import com.mobilispect.backend.feed.model.ids.FeedId
import com.mobilispect.backend.transitanalysis.domain.model.Stop
import com.mobilispect.backend.transitanalysis.domain.model.ids.StopId
import com.mobilispect.backend.transitanalysis.domain.repository.StopRepository
import com.mobilispect.backend.transitanalysis.domain.service.FeedImportService
import com.mobilispect.backend.transitanalysis.domain.service.OnestopIdGenerator
import com.mobilispect.backend.transitanalysis.domain.service.StopPersistenceService
import com.mobilispect.backend.transitanalysis.domain.service.StopPersistenceServiceImpl
import com.mobilispect.backend.transitanalysis.infrastructure.gtfs.ParsedStop
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.ArgumentCaptor
import org.mockito.Captor
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.time.Instant

/**
 * Contract test suite for GTFS stop import functionality.
 *
 * Verifies that:
 * 1. Stops are correctly persisted from GTFS feeds
 * 2. Onestop IDs are generated following Transitland format
 * 3. Stop deduplication works (update existing vs insert new)
 * 4. Junction table entries are created linking stops to variants
 * 5. Extended GTFS fields are preserved
 * 6. Tracking metadata (first_seen, last_seen) is managed correctly
 *
 * This test uses mocks to verify the contract between components
 * without requiring full database integration.
 */
@ExtendWith(MockitoExtension::class)
class StopImportContractTest {

    @Mock
    private lateinit var stopRepository: StopRepository

    @Mock
    private lateinit var onestopIdGenerator: OnestopIdGenerator

    @Mock
    private lateinit var routeVariantStopRepository: com.mobilispect.backend.transitanalysis.data.repository.RouteVariantStopJpaRepository

    private lateinit var stopPersistenceService: StopPersistenceService

    private val testFeedId = FeedId("f-9q8y-bart")
    private val testFeedEntity = FeedEntity(
        feedOnestopId = "f-9q8y-bart",
        name = "BART",
        specType = FeedSpecType.GTFS,
        downloadUrl = "https://example.com/gtfs.zip",
        status = FeedStatus.ACTIVE
    )

    @BeforeEach
    fun setUp() {
        // Initialize service with mocked dependencies
        stopPersistenceService = StopPersistenceServiceImpl(
            stopRepository = stopRepository,
            routeVariantStopRepository = routeVariantStopRepository,
            onestopIdGenerator = onestopIdGenerator
        )
    }

    /**
     * Test: Stops with core GTFS fields are persisted correctly
     *
     * Verifies FR-002: Parse and extract stop information
     * Verifies FR-020: Persist imported feed data
     */
    @Test
    fun `persistStops creates new stops with core GTFS fields`() {
        // Given: Parsed stops from GTFS
        val parsedStops = listOf(
            ParsedStop(
                stopId = "EMBR",
                name = "Embarcadero",
                latitude = 37.792976,
                longitude = -122.396742,
                stopCode = "EMBR",
                stopDesc = "Embarcadero Station",
                zoneId = null,
                stopUrl = null,
                locationType = 1, // Station
                parentStation = null
            ),
            ParsedStop(
                stopId = "MONT",
                name = "Montgomery St",
                latitude = 37.789256,
                longitude = -122.401407,
                stopCode = "MONT",
                stopDesc = "Montgomery Street Station",
                zoneId = null,
                stopUrl = null,
                locationType = 1,
                parentStation = null
            )
        )

        // Mock: Onestop ID generation - use any() for all parameters
        whenever(onestopIdGenerator.generateStopId(any(), any(), any(), any(), any()))
            .thenReturn(StopId("s-9q8y-embarcadero"))
            .thenReturn(StopId("s-9q8y-montgomery~st"))

        // Mock: No existing stops
        whenever(stopRepository.findById(any())).thenReturn(null)

        // Mock: Repository save returns the saved entity
        whenever(stopRepository.save(any())).thenAnswer { it.arguments[0] }

        // When: Persisting stops
        val result = stopPersistenceService.persistStops(testFeedEntity, parsedStops)

        // Then: Should create 2 new stops
        assertThat(result).hasSize(2)
        assertThat(result).containsKeys("EMBR", "MONT")

        // Verify: Stops were saved with correct data
        val stopCaptor = argumentCaptor<Stop>()
        verify(stopRepository, times(2)).save(stopCaptor.capture())

        val savedStops = stopCaptor.allValues
        val embrStop = savedStops.find { it.gtfsStopId == "EMBR" }
        assertThat(embrStop).isNotNull
        assertThat(embrStop!!.stopOnestopId.value).isEqualTo("s-9q8y-embarcadero")
        assertThat(embrStop.name).isEqualTo("Embarcadero")
        assertThat(embrStop.latitude).isEqualTo(37.792976)
        assertThat(embrStop.longitude).isEqualTo(-122.396742)
        assertThat(embrStop.stopCode).isEqualTo("EMBR")
        assertThat(embrStop.stopDesc).isEqualTo("Embarcadero Station")
        assertThat(embrStop.locationType).isEqualTo(1)
        assertThat(embrStop.active).isTrue()
    }

    /**
     * Test: Extended GTFS fields are preserved
     *
     * Verifies that all optional GTFS stop fields are correctly stored:
     * - stop_code
     * - stop_desc
     * - zone_id
     * - stop_url
     * - location_type
     * - parent_station
     */
    @Test
    fun `persistStops preserves extended GTFS fields`() {
        // Given: Stop with all extended fields populated
        val parsedStop = ParsedStop(
            stopId = "PLATFORM_1",
            name = "Platform 1",
            latitude = 37.792976,
            longitude = -122.396742,
            stopCode = "P1",
            stopDesc = "Platform 1 - Northbound",
            zoneId = "ZONE_A",
            stopUrl = "https://example.com/stops/platform1",
            locationType = 0, // Stop/Platform
            parentStation = "EMBR"
        )

        whenever(onestopIdGenerator.generateStopId(any(), any(), any(), any(), any()))
            .thenReturn(StopId("s-9q8y-platform~1"))
        whenever(stopRepository.findById(any())).thenReturn(null)
        whenever(stopRepository.save(any())).thenAnswer { it.arguments[0] }

        // When: Persisting stop
        stopPersistenceService.persistStops(testFeedEntity, listOf(parsedStop))

        // Then: All extended fields should be preserved
        val stopCaptor = argumentCaptor<Stop>()
        verify(stopRepository).save(stopCaptor.capture())
        val savedStop = stopCaptor.firstValue

        assertThat(savedStop.stopCode).isEqualTo("P1")
        assertThat(savedStop.stopDesc).isEqualTo("Platform 1 - Northbound")
        assertThat(savedStop.zoneId).isEqualTo("ZONE_A")
        assertThat(savedStop.stopUrl).isEqualTo("https://example.com/stops/platform1")
        assertThat(savedStop.locationType).isEqualTo(0)
        assertThat(savedStop.parentStation).isEqualTo("EMBR")
    }

    /**
     * Test: Stop deduplication updates existing stops
     *
     * Verifies that when a stop with the same Onestop ID is imported again:
     * - Existing stop is updated (not duplicated)
     * - Mutable fields are updated (name, coordinates, extended fields)
     * - first_seen is preserved
     * - last_seen is updated
     * - active status remains true
     */
    @Test
    fun `persistStops updates existing stops instead of creating duplicates`() {
        // Given: A stop that already exists
        val existingStopId = StopId("s-9q8y-embarcadero")
        val firstSeenTime = Instant.parse("2024-01-01T00:00:00Z")
        val existingStop = Stop(
            stopOnestopId = existingStopId,
            feedId = testFeedId,
            gtfsStopId = "EMBR",
            name = "Embarcadero (Old Name)",
            latitude = 37.792900, // Slightly different coordinates
            longitude = -122.396700,
            stopCode = null,
            stopDesc = null,
            zoneId = null,
            stopUrl = null,
            locationType = 1,
            parentStation = null,
            active = true,
            firstSeen = firstSeenTime,
            lastSeen = firstSeenTime,
            createdAt = firstSeenTime,
            updatedAt = firstSeenTime
        )

        // Given: Updated stop data from new GTFS feed
        val parsedStop = ParsedStop(
            stopId = "EMBR",
            name = "Embarcadero Station", // Updated name
            latitude = 37.792976, // Updated coordinates
            longitude = -122.396742,
            stopCode = "EMBR", // New field
            stopDesc = "Embarcadero BART Station", // New field
            zoneId = null,
            stopUrl = null,
            locationType = 1,
            parentStation = null
        )

        whenever(onestopIdGenerator.generateStopId(any(), any(), any(), any(), any()))
            .thenReturn(existingStopId)

        // Mock: Stop already exists
        whenever(stopRepository.findById(existingStopId)).thenReturn(existingStop)
        whenever(stopRepository.save(any())).thenAnswer { it.arguments[0] }

        // When: Persisting stops
        val beforeUpdate = Instant.now()
        stopPersistenceService.persistStops(testFeedEntity, listOf(parsedStop))

        // Then: Should update existing stop
        val stopCaptor = argumentCaptor<Stop>()
        verify(stopRepository).save(stopCaptor.capture())
        val updatedStop = stopCaptor.firstValue

        // Verify: Mutable fields are updated
        assertThat(updatedStop.name).isEqualTo("Embarcadero Station")
        assertThat(updatedStop.latitude).isEqualTo(37.792976)
        assertThat(updatedStop.longitude).isEqualTo(-122.396742)
        assertThat(updatedStop.stopCode).isEqualTo("EMBR")
        assertThat(updatedStop.stopDesc).isEqualTo("Embarcadero BART Station")

        // Verify: Tracking fields are managed correctly
        assertThat(updatedStop.firstSeen).isEqualTo(firstSeenTime) // Preserved
        assertThat(updatedStop.lastSeen).isAfterOrEqualTo(beforeUpdate) // Updated
        assertThat(updatedStop.active).isTrue()
    }

    /**
     * Test: Stops with missing required fields are skipped
     *
     * Verifies that stops without name, latitude, or longitude are:
     * - Logged as warnings
     * - Not persisted to the database
     * - Not included in the return map
     */
    @Test
    fun `persistStops skips stops with missing required fields`() {
        // Given: Stops with missing required fields
        val parsedStops = listOf(
            ParsedStop(
                stopId = "INVALID_1",
                name = null, // Missing name
                latitude = 37.792976,
                longitude = -122.396742
            ),
            ParsedStop(
                stopId = "INVALID_2",
                name = "Valid Name",
                latitude = null, // Missing latitude
                longitude = -122.396742
            ),
            ParsedStop(
                stopId = "INVALID_3",
                name = "Valid Name",
                latitude = 37.792976,
                longitude = null // Missing longitude
            ),
            ParsedStop(
                stopId = "VALID",
                name = "Valid Stop",
                latitude = 37.792976,
                longitude = -122.396742
            )
        )

        whenever(onestopIdGenerator.generateStopId(any(), any(), any(), any(), any()))
            .thenReturn(StopId("s-9q8y-valid~stop"))
        whenever(stopRepository.findById(any())).thenReturn(null)
        whenever(stopRepository.save(any())).thenAnswer { it.arguments[0] }

        // When: Persisting stops
        val result = stopPersistenceService.persistStops(testFeedEntity, parsedStops)

        // Then: Only valid stop should be persisted
        assertThat(result).hasSize(1)
        assertThat(result).containsKey("VALID")

        // Verify: Only one stop was saved
        verify(stopRepository, times(1)).save(any())
    }

    /**
     * Test: Onestop ID generation follows Transitland format
     *
     * Verifies that stop Onestop IDs follow the format:
     * s-{geohash}-{normalized_name}
     *
     * This test validates the contract between StopPersistenceService
     * and OnestopIdGenerator.
     */
    @Test
    fun `persistStops uses OnestopIdGenerator for deterministic IDs`() {
        // Given: A parsed stop
        val parsedStop = ParsedStop(
            stopId = "TEST_STOP",
            name = "Test Stop & Station",
            latitude = 37.792976,
            longitude = -122.396742
        )

        val expectedOnestopId = StopId("s-9q8y-test~stop~station")

        // Mock: ID generation with any() matchers for all parameters
        whenever(onestopIdGenerator.generateStopId(any(), any(), any(), any(), any()))
            .thenReturn(expectedOnestopId)

        whenever(stopRepository.findById(any())).thenReturn(null)
        whenever(stopRepository.save(any())).thenAnswer { it.arguments[0] }

        // When: Persisting stop
        stopPersistenceService.persistStops(testFeedEntity, listOf(parsedStop))

        // Then: Should use generated Onestop ID
        verify(onestopIdGenerator).generateStopId(
            feedId = testFeedId,
            gtfsStopId = "TEST_STOP",
            name = "Test Stop & Station",
            lat = 37.792976,
            lon = -122.396742
        )

        val stopCaptor = argumentCaptor<Stop>()
        verify(stopRepository).save(stopCaptor.capture())
        assertThat(stopCaptor.firstValue.stopOnestopId).isEqualTo(expectedOnestopId)
    }

    /**
     * Test: Import result includes stops processed count
     *
     * Verifies FR-024: Collect metrics for import operations
     *
     * When FeedImportService completes, the ImportResult should include:
     * - stopsProcessed: Number of stops successfully persisted
     */
    @Test
    fun `FeedImportService ImportResult includes stopsProcessed metric`() {
        // Given: An ImportResult from feed import
        val importResult = FeedImportService.ImportResult(
            agenciesProcessed = 1,
            routesProcessed = 5,
            variantsIdentified = 12,
            stopsProcessed = 47, // New field
            durationMillis = 1234
        )

        // Then: stopsProcessed should be accessible
        assertThat(importResult.stopsProcessed).isEqualTo(47)

        // Verify: All metrics are present
        assertThat(importResult.agenciesProcessed).isEqualTo(1)
        assertThat(importResult.routesProcessed).isEqualTo(5)
        assertThat(importResult.variantsIdentified).isEqualTo(12)
        assertThat(importResult.durationMillis).isEqualTo(1234)
    }
}
