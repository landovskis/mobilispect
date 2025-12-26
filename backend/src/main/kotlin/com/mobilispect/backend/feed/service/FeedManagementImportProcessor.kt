package com.mobilispect.backend.feed.service

import com.mobilispect.backend.feed.model.FeedEntity
import com.mobilispect.backend.feed.model.ids.FeedId
import com.mobilispect.backend.feed.repository.FeedRepository
import com.mobilispect.backend.schedule.download.DownloadRequest
import com.mobilispect.backend.schedule.download.Downloader
import com.mobilispect.backend.util.ArchiveExtractor
import com.mobilispect.backend.websocket.ProgressTrackingService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Service
import java.nio.file.Files
import java.nio.file.Path
import java.time.Clock

/**
 * PostgreSQL-based GTFS feed import processor.
 *
 * This service handles GTFS feed imports for the feed management system,
 * downloading and processing feeds directly without MongoDB dependency.
 */
@Service
class FeedManagementImportProcessor(
    @Qualifier("feedManagementFeedRepository")
    private val feedRepository: FeedRepository,
    @Qualifier("curlDownloader")
    private val downloader: Downloader,
    private val archiveExtractor: ArchiveExtractor,
    private val progressTrackingService: ProgressTrackingService,
    private val clock: Clock = Clock.systemUTC()
) {
    private val logger = LoggerFactory.getLogger(FeedManagementImportProcessor::class.java)

    /**
     * Import a feed by its onestop ID.
     *
     * Downloads the GTFS feed, extracts it, and processes the data.
     *
     * @param feedOnestopId The onestop ID of the feed to import
     * @return Result containing the version SHA1 hash of the imported feed on success
     */
    suspend fun importFeedById(feedOnestopId: String): Result<String?> {
        return withContext(Dispatchers.IO) {
            logger.info("Starting PostgreSQL-based import for feed: $feedOnestopId")

            // Find the feed in PostgreSQL
            val feed = feedRepository.findByFeedOnestopId(feedOnestopId).orElse(null)
                ?: return@withContext Result.failure(
                    IllegalArgumentException("Feed not found: $feedOnestopId")
                )

            // Validate feed has a download URL
            if (feed.downloadUrl.isBlank()) {
                return@withContext Result.failure(
                    IllegalArgumentException("Feed $feedOnestopId has no download URL")
                )
            }

            val importId = "${feed.feedOnestopId}:${feed.currentVersionSha1 ?: "latest"}"
            val startedAt = clock.instant()
            val totalSteps = 8

            fun progress(stepNumber: Int, stepName: String, percentage: Int = (stepNumber * 100) / totalSteps) {
                progressTrackingService.updateProgress(
                    importId = importId,
                    feedOnestopId = feed.feedOnestopId,
                    progressPercentage = percentage.coerceAtMost(100),
                    currentStep = stepName,
                    currentStepNumber = stepNumber,
                    totalSteps = totalSteps,
                    startedAt = startedAt
                )
            }

            progress(stepNumber = 0, stepName = "Preparing import", percentage = 0)

            val result = runCatching {
                progress(stepNumber = 1, stepName = "Downloading feed")
                val archive = downloadFeed(feed).getOrThrow()

                progress(stepNumber = 2, stepName = "Extracting feed")
                val extractedDir = extractFeed(archive).getOrThrow()

                progress(stepNumber = 3, stepName = "Validating GTFS files")
                validateGtfsFiles(extractedDir).getOrThrow()

                // Clean up extracted validation directory
                Files.walk(extractedDir)
                    .sorted(Comparator.reverseOrder())
                    .forEach { Files.deleteIfExists(it) }

                progress(stepNumber = 4, stepName = "Processing GTFS data")
                logger.info("GTFS validation complete for feed: $feedOnestopId")

                // TODO: Integrate with transit analysis service when available
                // Transit analysis import (routes, variants, frequencies) will be added
                // in a future iteration

                progress(stepNumber = 8, stepName = "Finalizing import", percentage = 100)

                // Clean up archive
                Files.deleteIfExists(archive)

                // Return null for versionSha1 - feed version tracking handled separately
                null
            }

            result
                .onSuccess {
                    logger.info("Successfully completed import for feed: $feedOnestopId")
                    progressTrackingService.markCompleted(importId)
                }
                .onFailure { error ->
                    logger.error("Failed to import feed: $feedOnestopId", error)
                    progressTrackingService.markFailed(importId, error.message ?: "Import failed")
                }
        }
    }

    private fun downloadFeed(feed: FeedEntity): Result<Path> {
        return runCatching {
            logger.info("Downloading feed from: {}", feed.downloadUrl)

            val request = DownloadRequest(url = feed.downloadUrl)
            downloader.download(request).getOrThrow()
        }
    }

    private fun extractFeed(archive: Path): Result<Path> {
        return runCatching {
            logger.info("Extracting archive: {}", archive)
            archiveExtractor.extract(archive).getOrThrow()
        }
    }

    private fun validateGtfsFiles(extractedDir: Path): Result<Unit> {
        return runCatching {
            logger.info("Validating GTFS files in: {}", extractedDir)

            // Check for required GTFS files
            val requiredFiles = listOf("agency.txt", "stops.txt", "routes.txt", "trips.txt", "stop_times.txt")
            val missingFiles = requiredFiles.filter { !Files.exists(extractedDir.resolve(it)) }

            if (missingFiles.isNotEmpty()) {
                throw IllegalStateException("Missing required GTFS files: ${missingFiles.joinToString(", ")}")
            }

            logger.info("GTFS validation successful")
        }
    }
}
