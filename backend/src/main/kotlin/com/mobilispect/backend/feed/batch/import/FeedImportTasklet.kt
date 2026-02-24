package com.mobilispect.backend.feed.batch.import

import com.mobilispect.backend.feed.domain.model.ids.FeedId
import kotlinx.coroutines.runBlocking
import org.slf4j.LoggerFactory
import org.springframework.batch.core.ExitStatus
import org.springframework.batch.core.configuration.annotation.StepScope
import org.springframework.batch.core.scope.context.ChunkContext
import org.springframework.batch.core.step.StepContribution
import org.springframework.batch.core.step.tasklet.Tasklet
import org.springframework.batch.infrastructure.repeat.RepeatStatus
import org.springframework.stereotype.Component

@Component
@StepScope
class FeedImportTasklet(private val gtfsFeedReader: GTFSFeedReader) : Tasklet {

  private val logger = LoggerFactory.getLogger(FeedImportTasklet::class.java)

  override fun execute(contribution: StepContribution, chunkContext: ChunkContext): RepeatStatus {
    val params = chunkContext.stepContext.jobParameters
    val feedOnestopId =
      params["feedOnestopId"] as? String ?: error("feedOnestopId job parameter is required")

    val result = runBlocking { gtfsFeedReader.importFeedById(FeedId(feedOnestopId)) }

    result.onFailure { throwable ->
      logger.error("Feed import failed for $feedOnestopId", throwable)
      contribution.exitStatus = ExitStatus.FAILED
      throw throwable
    }

    return RepeatStatus.FINISHED
  }
}
