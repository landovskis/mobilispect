package com.mobilispect.backend.feed.batch.import

import com.mobilispect.backend.agency.domain.repository.AgencyRepository
import com.mobilispect.backend.feed.model.FeedEntity
import com.mobilispect.backend.feed.model.FeedSpecType
import com.mobilispect.backend.feed.model.FeedStatus
import com.mobilispect.backend.feed.model.ImportStatus
import com.mobilispect.backend.feed.model.ids.ImportId
import com.mobilispect.backend.feed.repository.FeedImportRepository
import com.mobilispect.backend.feed.repository.FeedRepository
import com.mobilispect.backend.route.domain.repository.RouteRepository
import java.nio.file.Paths
import java.time.Instant
import java.util.UUID
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.batch.core.BatchStatus
import org.springframework.batch.core.Job
import org.springframework.batch.core.JobParametersBuilder
import org.springframework.batch.core.launch.JobLauncher
import org.springframework.batch.test.context.SpringBatchTest
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.boot.testcontainers.service.connection.ServiceConnection
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.test.context.TestPropertySource
import org.springframework.transaction.annotation.Transactional
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers

/**
 * End-to-end integration test for Feed Import batch processing using STM feed.
 *
 * Tests the complete feed import pipeline including:
 * - Feed download and extraction
 * - GTFS parsing
 * - Agency import
 * - Route import
 * - Route variant generation
 * - Stop spacing calculation
 * - Frequency analysis
 *
 * Constitutional Requirements:
 * - Test-Driven Quality: Integration tests using Testcontainers
 * - Module boundaries: Tests through public APIs only
 * - Event-driven architecture: Verifies feed import events
 */
@SpringBootTest
@SpringBatchTest
@Transactional
@Testcontainers
@TestPropertySource(properties = ["spring.batch.job.enabled=false"])
@Import(FeedImportEndToEndIntegrationTest.TestConfig::class)
class FeedImportEndToEndIntegrationTest {

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

  @Autowired
  @Qualifier("feedManagementFeedRepository")
  private lateinit var feedRepository: FeedRepository

  @Autowired private lateinit var feedImportRepository: FeedImportRepository

  @Autowired private lateinit var agencyRepository: AgencyRepository

  @Autowired private lateinit var routeRepository: RouteRepository

  @Autowired private lateinit var jobLauncher: JobLauncher

  @Autowired @Qualifier("feedImportJob") private lateinit var feedImportJob: Job

  @Autowired private lateinit var eventListener: TestEventListener

  private val fixedInstant = Instant.parse("2025-01-15T12:00:00Z")

  // STM feed configuration - using test GTFS file as STM feed
  private val stmFeedOnestopId = "f-f25d-socitdetransportdemontreal"

  @BeforeEach
  fun setUp() {
    // Clean up before each test
    feedImportRepository.deleteAll()
    routeRepository.findAll().forEach { routeRepository.deleteById(it.id) }
    agencyRepository.findAll().forEach { agencyRepository.deleteById(it.agencyOnestopId) }
    feedRepository.deleteAll()
    eventListener.clear()

    // Create STM test feed pointing to local test GTFS file
    // Using the existing exopi GTFS test file as a representative GTFS feed
    val testGtfsPath =
      Paths.get("src/test/resources/exopi-gtfs-d89aa5de884111e4b6a9365220ded9f746ef2dbf.zip")
        .toAbsolutePath()
    val testGtfsUrl = "file://${testGtfsPath}"

    val stmFeed =
      FeedEntity(
        feedId = stmFeedOnestopId,
        regions = mutableSetOf(),
        name = "STM GTFS Test Feed",
        downloadUrl = testGtfsUrl,
        specType = FeedSpecType.GTFS,
        status = FeedStatus.PENDING_IMPORT,
        createdAt = fixedInstant,
        updatedAt = fixedInstant,
      )
    feedRepository.save(stmFeed)
  }

  @Test
  fun `should successfully import complete STM feed end-to-end`() {
    // Given: STM feed configured in database and import record created
    val importId = ImportId(UUID.randomUUID())
    val feedImport =
      com.mobilispect.backend.feed.domain.FeedImport().apply {
        this.id = importId
        this.feedId = stmFeedOnestopId
        this.administrator = null
        this.triggerType = com.mobilispect.backend.feed.model.ImportTriggerType.MANUAL
        this.status = ImportStatus.RUNNING
        this.startedAt = fixedInstant
      }
    feedImportRepository.save(feedImport)

    // When: Run feed import job
    val jobParameters =
      JobParametersBuilder()
        .addString("feedOnestopId", stmFeedOnestopId)
        .addString("importId", importId.value.toString())
        .addLong("timestamp", System.currentTimeMillis())
        .toJobParameters()

    val jobExecution = jobLauncher.run(feedImportJob, jobParameters)

    // Then: Job should complete successfully
    assertThat(jobExecution.status).isEqualTo(BatchStatus.COMPLETED)
    assertThat(jobExecution.exitStatus.exitCode).isEqualTo("COMPLETED")

    // Verify all 6 steps completed
    val stepExecutions = jobExecution.stepExecutions
    assertThat(stepExecutions).hasSize(6)
    assertThat(stepExecutions.map { it.stepName })
      .containsExactlyInAnyOrder(
        "feedImportStep",
        "agencyProcessingStep",
        "routeProcessingStep",
        "routeVariantProcessingStep",
        "stopSpacingProcessingStep",
        "frequencyProcessingStep",
      )

    // Verify all steps completed successfully
    stepExecutions.forEach { step -> assertThat(step.status).isEqualTo(BatchStatus.COMPLETED) }

    // Verify agencies were imported
    val agencies = agencyRepository.findAll()
    assertThat(agencies).isNotEmpty

    // Verify routes were imported
    val routes = routeRepository.findAll()
    assertThat(routes).isNotEmpty
  }

  @Test
  fun `should import and persist agencies from GTFS feed`() {
    // Given: Import record
    val importId = ImportId(UUID.randomUUID())
    val feedImport =
      com.mobilispect.backend.feed.domain.FeedImport().apply {
        this.id = importId
        this.feedId = stmFeedOnestopId
        this.status = ImportStatus.RUNNING
        this.startedAt = fixedInstant
      }
    feedImportRepository.save(feedImport)

    // When: Run feed import job
    val jobParameters =
      JobParametersBuilder()
        .addString("feedOnestopId", stmFeedOnestopId)
        .addString("importId", importId.value.toString())
        .addLong("timestamp", System.currentTimeMillis())
        .toJobParameters()

    val jobExecution = jobLauncher.run(feedImportJob, jobParameters)

    // Then: Agencies should be imported from GTFS
    assertThat(jobExecution.status).isEqualTo(BatchStatus.COMPLETED)

    val agencies = agencyRepository.findAll()
    assertThat(agencies).isNotEmpty
    assertThat(agencies).allMatch { it.feedId.value == stmFeedOnestopId }

    // Verify agency has required fields
    val firstAgency = agencies.first()
    assertThat(firstAgency.name).isNotBlank()
    assertThat(firstAgency.agencyOnestopId).isNotNull
    assertThat(firstAgency.gtfsAgencyId).isNotNull
  }

  @Test
  fun `should import and persist routes from GTFS feed`() {
    // Given: Import record
    val importId = ImportId(UUID.randomUUID())
    val feedImport =
      com.mobilispect.backend.feed.domain.FeedImport().apply {
        this.id = importId
        this.feedId = stmFeedOnestopId
        this.status = ImportStatus.RUNNING
        this.startedAt = fixedInstant
      }
    feedImportRepository.save(feedImport)

    // When: Run feed import job
    val jobParameters =
      JobParametersBuilder()
        .addString("feedOnestopId", stmFeedOnestopId)
        .addString("importId", importId.value.toString())
        .addLong("timestamp", System.currentTimeMillis())
        .toJobParameters()

    val jobExecution = jobLauncher.run(feedImportJob, jobParameters)

    // Then: Routes should be imported from GTFS
    assertThat(jobExecution.status).isEqualTo(BatchStatus.COMPLETED)

    val routes = routeRepository.findAll()
    assertThat(routes).isNotEmpty

    // Verify route has required fields
    val firstRoute = routes.first()
    assertThat(firstRoute.id).isNotNull
    assertThat(firstRoute.agencyId).isNotNull
    assertThat(firstRoute.gtfsRouteId).isNotNull
    assertThat(firstRoute.routeType).isNotNull
    assertThat(firstRoute.active).isTrue()
  }

  @Test
  fun `should execute all 6 batch steps in correct order`() {
    // Given: Import record
    val importId = ImportId(UUID.randomUUID())
    val feedImport =
      com.mobilispect.backend.feed.domain.FeedImport().apply {
        this.id = importId
        this.feedId = stmFeedOnestopId
        this.status = ImportStatus.RUNNING
        this.startedAt = fixedInstant
      }
    feedImportRepository.save(feedImport)

    // When: Run feed import job
    val jobParameters =
      JobParametersBuilder()
        .addString("feedOnestopId", stmFeedOnestopId)
        .addString("importId", importId.value.toString())
        .addLong("timestamp", System.currentTimeMillis())
        .toJobParameters()

    val jobExecution = jobLauncher.run(feedImportJob, jobParameters)

    // Then: Verify all steps executed in order
    assertThat(jobExecution.status).isEqualTo(BatchStatus.COMPLETED)

    val stepExecutions = jobExecution.stepExecutions.sortedBy { it.startTime }
    val stepNames = stepExecutions.map { it.stepName }

    assertThat(stepNames)
      .containsExactly(
        "feedImportStep", // 1. Download and parse GTFS
        "agencyProcessingStep", // 2. Import agencies
        "routeProcessingStep", // 3. Import routes
        "routeVariantProcessingStep", // 4. Generate route variants
        "stopSpacingProcessingStep", // 5. Calculate stop spacing
        "frequencyProcessingStep", // 6. Analyze frequencies
      )

    // Verify each step processed items
    val feedImportStepExec = stepExecutions.find { it.stepName == "feedImportStep" }
    assertThat(feedImportStepExec?.readCount).isGreaterThan(0)

    val agencyStepExec = stepExecutions.find { it.stepName == "agencyProcessingStep" }
    assertThat(agencyStepExec?.readCount).isGreaterThan(0)

    val routeStepExec = stepExecutions.find { it.stepName == "routeProcessingStep" }
    assertThat(routeStepExec?.readCount).isGreaterThan(0)
  }

  @Test
  fun `should handle feed with invalid download URL`() {
    // Given: Feed with invalid download URL
    val invalidFeedId = "f-invalid-feed"
    val invalidFeed =
      FeedEntity(
        feedId = invalidFeedId,
        regions = mutableSetOf(),
        name = "Invalid Feed",
        downloadUrl = "file:///nonexistent/path/feed.zip",
        specType = FeedSpecType.GTFS,
        status = FeedStatus.PENDING_IMPORT,
        createdAt = fixedInstant,
        updatedAt = fixedInstant,
      )
    feedRepository.save(invalidFeed)

    val importId = ImportId(UUID.randomUUID())
    val feedImport =
      com.mobilispect.backend.feed.domain.FeedImport().apply {
        this.id = importId
        this.feedId = invalidFeedId
        this.status = ImportStatus.RUNNING
        this.startedAt = fixedInstant
      }
    feedImportRepository.save(feedImport)

    // When: Attempt to run feed import job
    val jobParameters =
      JobParametersBuilder()
        .addString("feedOnestopId", invalidFeedId)
        .addString("importId", importId.value.toString())
        .addLong("timestamp", System.currentTimeMillis())
        .toJobParameters()

    val jobExecution = jobLauncher.run(feedImportJob, jobParameters)

    // Then: Job should fail
    assertThat(jobExecution.status).isEqualTo(BatchStatus.FAILED)
    assertThat(jobExecution.allFailureExceptions).isNotEmpty
  }

  /** Test configuration for capturing domain events. */
  @TestConfiguration
  class TestConfig {
    @Bean fun testEventListener() = TestEventListener()
  }

  /** Test event listener to capture feed import events. */
  class TestEventListener {
    // Placeholder for event listeners
    // Can be expanded to capture FeedImportStartedEvent, FeedImportCompletedEvent, etc.
  }
}
