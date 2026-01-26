package com.mobilispect.backend.route.batch.classification

import com.mobilispect.backend.feed.api.GTFSData
import com.mobilispect.backend.feed.api.handler.GTFSDataBundle
import com.mobilispect.backend.feed.api.handler.ImportContext
import com.mobilispect.backend.feed.api.handler.ImportResult
import com.mobilispect.backend.feed.domain.model.ids.FeedId
import com.mobilispect.backend.feed.model.ids.ImportId
import com.mobilispect.backend.route.handler.RouteClassificationFeedDataHandler
import java.time.Instant
import java.time.ZoneOffset
import org.slf4j.LoggerFactory
import org.springframework.batch.core.configuration.annotation.StepScope
import org.springframework.batch.core.scope.context.ChunkContext
import org.springframework.batch.core.step.StepContribution
import org.springframework.batch.core.step.tasklet.Tasklet
import org.springframework.batch.infrastructure.repeat.RepeatStatus
import org.springframework.stereotype.Component

@Component
@StepScope
class RouteClassificationTasklet(
  private val routeClassificationFeedDataHandler: RouteClassificationFeedDataHandler
) : Tasklet {

  private val logger = LoggerFactory.getLogger(RouteClassificationTasklet::class.java)

  override fun execute(contribution: StepContribution, chunkContext: ChunkContext): RepeatStatus {
    val params = chunkContext.stepContext.jobParameters
    val feedOnestopId =
      params["feedOnestopId"] as? String ?: error("feedOnestopId job parameter is required")
    val importIdString =
      params["importId"] as? String ?: error("importId job parameter is required")

    val jobExecution = chunkContext.stepContext.stepExecution.jobExecution
    val parsedData =
      jobExecution.executionContext.get("parsedData") as? GTFSData
        ?: throw IllegalStateException("ParsedGtfsData not found in job execution context")

    val feedId = FeedId(feedOnestopId)
    val importContext =
      ImportContext(
        importId = ImportId.fromString(importIdString),
        startedAt = jobExecution.startTime?.toInstant(ZoneOffset.UTC) ?: Instant.now(),
      )

    val bundle =
      GTFSDataBundle(
        feedId = feedId,
        routes = parsedData.routes,
        trips = parsedData.trips,
        stops = parsedData.stops,
        shapes = parsedData.shapes,
      )

    val result = routeClassificationFeedDataHandler.handle(feedId, bundle, importContext)

    when (result) {
      is ImportResult.Success -> {
        logger.info(
          "Route classification completed for feed {}: {} records processed",
          feedId.value,
          result.recordsProcessed,
        )
      }
      is ImportResult.PartialSuccess -> {
        logger.warn(
          "Route classification partially completed for feed {}: {} succeeded, {} failed",
          feedId.value,
          result.recordsProcessed,
          result.errors.size,
        )
      }
      is ImportResult.Failure -> {
        logger.error(
          "Route classification failed for feed {}: {}",
          feedId.value,
          result.error.message,
        )
        throw IllegalStateException(result.error.message ?: "Route classification failed")
      }
    }

    return RepeatStatus.FINISHED
  }
}
