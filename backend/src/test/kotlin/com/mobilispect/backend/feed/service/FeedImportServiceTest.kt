package com.mobilispect.backend.feed.service

import com.mobilispect.backend.feed.model.Administrator
import com.mobilispect.backend.feed.model.FeedEntity
import com.mobilispect.backend.feed.model.FeedImport
import com.mobilispect.backend.feed.model.FeedSpecType
import com.mobilispect.backend.feed.model.FeedStatus
import com.mobilispect.backend.feed.model.ImportStatus
import com.mobilispect.backend.feed.model.ImportTriggerType
import com.mobilispect.backend.feed.model.ids.FeedId
import com.mobilispect.backend.feed.model.ids.ImportId
import com.mobilispect.backend.feed.repository.AdministratorRepository
import com.mobilispect.backend.feed.repository.FeedImportRepository
import com.mobilispect.backend.feed.repository.FeedRepository
import com.mobilispect.backend.websocket.ProgressTrackingService
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.assertThrows
import org.springframework.batch.core.Job
import org.springframework.batch.core.JobExecution
import org.springframework.batch.core.JobParameters
import org.springframework.batch.core.launch.JobLauncher
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.Optional
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class FeedImportServiceTest {
    private val feedRepository = mockk<FeedRepository>()
    private val feedImportRepository = mockk<FeedImportRepository>()
    private val administratorRepository = mockk<AdministratorRepository>()
    private val progressTrackingService = mockk<ProgressTrackingService>(relaxed = true)
    private val jobLauncher = mockk<JobLauncher>()
    private val feedImportJob = mockk<Job>()
    private val fixedInstant = Instant.parse("2024-01-01T00:00:00Z")
    private val clock: Clock = Clock.fixed(fixedInstant, ZoneOffset.UTC)

    private lateinit var service: FeedImportService

    @BeforeEach
    fun setUp() {
        service = FeedImportService(
            feedRepository,
            feedImportRepository,
            administratorRepository,
            progressTrackingService,
            jobLauncher,
            feedImportJob,
            clock
        )
    }

    @Test
    fun `starts an import and launches job`() {
        val feed = FeedEntity(
            feedOnestopId = FeedId("f-feed"),
            name = "Feed",
            specType = FeedSpecType.GTFS,
            downloadUrl = "http://example.com",
            status = FeedStatus.ACTIVE,
            createdAt = fixedInstant,
            updatedAt = fixedInstant
        ).apply { currentVersionSha1 = "abc123" }
        val admin = Administrator(username = "user", email = "user@example.com")
        val importId = ImportId.random()

        every { feedRepository.findById(FeedId("f-feed")) } returns Optional.of(feed)
        every { administratorRepository.findByUsername("user") } returns Optional.of(admin)
        every { feedImportRepository.save(any()) } answers { firstArg<FeedImport>().apply { id = importId } }
        every { jobLauncher.run(feedImportJob, any<JobParameters>()) } returns mockk<JobExecution>()

        val created = service.startImport("f-feed", "user", ImportTriggerType.MANUAL, force = true)

        assertEquals(importId, created.id)
        assertEquals(ImportStatus.RUNNING, created.status)
        assertEquals(feed.currentVersionSha1, created.versionSha1)
        verify {
            progressTrackingService.updateProgress(
                importId = importId.value.toString(),
                feedOnestopId = "f-feed",
                progressPercentage = 0,
                currentStep = "Starting import",
                currentStepNumber = 0,
                totalSteps = 8,
                startedAt = fixedInstant
            )
            jobLauncher.run(feedImportJob, any<JobParameters>())
        }
    }

    @Test
    fun `cancels import and marks it failed`() {
        val importId = ImportId.random()
        val feedImport = FeedImport(id = importId, status = ImportStatus.RUNNING, startedAt = fixedInstant)

        every { feedImportRepository.findById(importId) } returns Optional.of(feedImport)
        every { feedImportRepository.save(feedImport) } returns feedImport

        service.cancelImport(importId)

        assertEquals(ImportStatus.CANCELLED, feedImport.status)
        assertEquals(fixedInstant, feedImport.completedAt)
        verify { progressTrackingService.markFailed(importId.value.toString(), "Import cancelled by user") }
    }

    @Test
    fun `completes import and updates feed`() {
        val importId = ImportId.random()
        val feed = FeedEntity(
            feedOnestopId = FeedId("f-feed"),
            name = "Feed",
            specType = FeedSpecType.GTFS,
            downloadUrl = "http://example.com",
            status = FeedStatus.INACTIVE,
            createdAt = fixedInstant,
            updatedAt = fixedInstant
        )
        val feedImport = FeedImport(
            id = importId,
            feed = feed,
            status = ImportStatus.RUNNING,
            startedAt = fixedInstant
        )

        every { feedImportRepository.findById(importId) } returns Optional.of(feedImport)
        every { feedImportRepository.save(feedImport) } returns feedImport
        every { feedRepository.save(feed) } returns feed

        service.completeImport(importId, versionSha1 = "new-sha")

        assertEquals(ImportStatus.COMPLETED, feedImport.status)
        assertEquals("new-sha", feedImport.versionSha1)
        assertEquals(FeedStatus.ACTIVE, feed.status)
        assertNotNull(feedImport.completedAt)
        verify { progressTrackingService.markCompleted(importId.value.toString()) }
    }

    @Test
    fun `fails import and records error`() {
        val importId = ImportId.random()
        val feedImport = FeedImport(id = importId, status = ImportStatus.RUNNING, startedAt = fixedInstant)

        every { feedImportRepository.findById(importId) } returns Optional.of(feedImport)
        every { feedImportRepository.save(feedImport) } returns feedImport

        service.failImport(importId, "something went wrong")

        assertEquals(ImportStatus.FAILED, feedImport.status)
        assertEquals("something went wrong", feedImport.errorMessage)
        verify { progressTrackingService.markFailed(importId.value.toString(), "something went wrong") }
    }

    @Test
    fun `start import throws when feed missing`() {
        every { feedRepository.findById(FeedId("missing")) } returns Optional.empty()

        assertThrows<IllegalArgumentException> {
            service.startImport("missing", null, ImportTriggerType.MANUAL, force = false)
        }
    }
}
