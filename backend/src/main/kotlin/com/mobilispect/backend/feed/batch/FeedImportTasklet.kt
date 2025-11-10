package com.mobilispect.backend.feed.batch

import com.mobilispect.backend.feed.service.FeedImportService
import com.mobilispect.backend.feed.service.FeedManagementImportProcessor
import kotlinx.coroutines.runBlocking
import org.slf4j.LoggerFactory
import org.springframework.batch.core.StepContribution
import org.springframework.batch.core.configuration.annotation.StepScope
import org.springframework.batch.core.scope.context.ChunkContext
import org.springframework.batch.core.step.tasklet.Tasklet
import org.springframework.batch.repeat.RepeatStatus
import org.springframework.stereotype.Component
import java.util.UUID

@Component
@StepScope
class FeedImportTasklet(
    private val feedManagementImportProcessor: FeedManagementImportProcessor,
    private val feedImportService: FeedImportService
) : Tasklet {

    private val logger = LoggerFactory.getLogger(FeedImportTasklet::class.java)

    override fun execute(contribution: StepContribution, chunkContext: ChunkContext): RepeatStatus {
        val params = chunkContext.stepContext.jobParameters
        val feedOnestopId = params["feedOnestopId"] as? String
            ?: error("feedOnestopId job parameter is required")
        val importId = (params["importId"] as? String)?.let(UUID::fromString)
            ?: error("importId job parameter is required")

        runBlocking {
            feedManagementImportProcessor.importFeedById(feedOnestopId)
        }.onSuccess { versionSha1 ->
            feedImportService.completeImport(importId, versionSha1)
        }.onFailure { throwable ->
            logger.error("Feed import failed for $feedOnestopId", throwable)
            feedImportService.failImport(importId, throwable.message ?: "Import failed")
        }

        return RepeatStatus.FINISHED
    }
}
