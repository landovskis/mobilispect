package com.mobilispect.backend.feed.batch.import

import com.mobilispect.backend.feed.api.GTFSData
import org.slf4j.LoggerFactory
import org.springframework.batch.core.annotation.BeforeStep
import org.springframework.batch.core.configuration.annotation.StepScope
import org.springframework.batch.core.step.StepExecution
import org.springframework.batch.infrastructure.item.Chunk
import org.springframework.batch.infrastructure.item.ItemWriter
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component

@Component
@StepScope
class FeedImportWriter : ItemWriter<GTFSData> {
  private val logger = LoggerFactory.getLogger(FeedImportWriter::class.java)

  @Value("#{jobParameters['importId']}") lateinit var importId: String

  private var stepExecution: StepExecution? = null

  @BeforeStep
  fun beforeStep(stepExecution: StepExecution) {
    this.stepExecution = stepExecution
  }

  override fun write(chunk: Chunk<out GTFSData>) {
    val parsedData = chunk.items.firstOrNull() ?: return

    // Store parsed data in job execution context for subsequent steps
    stepExecution?.jobExecution?.executionContext?.put("parsedData", parsedData)
    logger.info(
      "Stored parsed data in execution context: {} routes, {} trips, {} stops",
      parsedData.routes.size,
      parsedData.trips.size,
      parsedData.stops.size,
    )
  }
}
