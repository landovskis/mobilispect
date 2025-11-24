package com.mobilispect.backend.feed.service

import com.mobilispect.backend.feed.model.FeedEntity
import com.mobilispect.backend.feed.model.FeedSpecType
import com.mobilispect.backend.feed.model.FeedStatus
import com.mobilispect.backend.feed.model.ids.FeedId
import com.mobilispect.backend.feed.repository.FeedRepository
import com.mobilispect.backend.schedule.download.DownloadRequest
import com.mobilispect.backend.schedule.download.Downloader
import com.mobilispect.backend.util.ArchiveExtractor
import com.mobilispect.backend.websocket.ProgressTrackingService
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import kotlin.io.path.createTempDirectory
import kotlin.io.path.createTempFile
import kotlin.io.path.deleteIfExists
import kotlin.io.path.exists
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import java.nio.file.Files
import java.nio.file.Path
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.Optional

class FeedManagementImportProcessorTest {
    private val feedRepository = mockk<FeedRepository>()
    private val downloader = mockk<Downloader>()
    private val archiveExtractor = mockk<ArchiveExtractor>()
    private val progressTrackingService = mockk<ProgressTrackingService>(relaxed = true)
    private val fixedInstant = Instant.parse("2024-01-02T00:00:00Z")
    private val clock: Clock = Clock.fixed(fixedInstant, ZoneOffset.UTC)
    private val createdPaths = mutableListOf<Path>()

    private val processor = FeedManagementImportProcessor(
        feedRepository,
        downloader,
        archiveExtractor,
        progressTrackingService,
        clock
    )

    @AfterTest
    fun cleanup() {
        createdPaths.reversed().forEach { path ->
            if (Files.isDirectory(path)) {
                Files.walk(path)
                    .sorted(Comparator.reverseOrder())
                    .forEach { Files.deleteIfExists(it) }
            } else {
                path.deleteIfExists()
            }
        }
        createdPaths.clear()
    }

    @Test
    fun `imports feed and cleans up extracted files`() = runTest {
        val feed = FeedEntity(
            feedOnestopId = FeedId("f-feed"),
            name = "Feed",
            specType = FeedSpecType.GTFS,
            downloadUrl = "http://example.com/feed.zip",
            status = FeedStatus.ACTIVE,
            createdAt = fixedInstant,
            updatedAt = fixedInstant
        ).apply { currentVersionSha1 = "v1" }
        val archive = createTempFile("feed", ".zip").also { createdPaths.add(it) }
        val extracted = createTempDirectory("gtfs").also { createdPaths.add(it) }
        listOf("agency.txt", "stops.txt", "routes.txt", "trips.txt", "stop_times.txt").forEach { name ->
            Files.createFile(extracted.resolve(name))
        }

        every { feedRepository.findById(FeedId("f-feed")) } returns Optional.of(feed)
        every { downloader.download(any<DownloadRequest>()) } returns Result.success(archive)
        every { archiveExtractor.extract(archive) } returns Result.success(extracted)

        val result = processor.importFeedById("f-feed")

        assertTrue(result.isSuccess)
        assertEquals("f-feed:v1", result.getOrNull())
        verify { downloader.download(match { it.url == feed.downloadUrl }) }
        verify { archiveExtractor.extract(archive) }
        verify { progressTrackingService.markCompleted("f-feed:v1") }
        assertFalse(extracted.exists())
    }

    @Test
    fun `returns failure when feed is missing`() = runTest {
        every { feedRepository.findById(FeedId("f-missing")) } returns Optional.empty()

        val result = processor.importFeedById("f-missing")

        assertTrue(result.isFailure)
        verify(exactly = 0) { downloader.download(any()) }
        verify(exactly = 0) { progressTrackingService.updateProgress(any(), any(), any(), any(), any(), any(), any()) }
    }

    @Test
    fun `fails when required GTFS files are missing`() = runTest {
        val feed = FeedEntity(
            feedOnestopId = FeedId("f-feed"),
            name = "Feed",
            specType = FeedSpecType.GTFS,
            downloadUrl = "http://example.com/feed.zip",
            status = FeedStatus.ACTIVE,
            createdAt = fixedInstant,
            updatedAt = fixedInstant
        )
        val archive = createTempFile("feed-missing", ".zip").also { createdPaths.add(it) }
        val extracted = createTempDirectory("gtfs-missing").also { createdPaths.add(it) }
        // Intentionally omit stop_times.txt
        listOf("agency.txt", "stops.txt", "routes.txt", "trips.txt").forEach { name ->
            Files.createFile(extracted.resolve(name))
        }

        every { feedRepository.findById(FeedId("f-feed")) } returns Optional.of(feed)
        every { downloader.download(any<DownloadRequest>()) } returns Result.success(archive)
        every { archiveExtractor.extract(archive) } returns Result.success(extracted)

        val result = processor.importFeedById("f-feed")

        assertTrue(result.isFailure)
        verify { progressTrackingService.markFailed("f-feed:latest", match { it.contains("Missing required GTFS files") }) }
    }
}
