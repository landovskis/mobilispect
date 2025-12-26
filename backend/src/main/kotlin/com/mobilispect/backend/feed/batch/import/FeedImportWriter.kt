package com.mobilispect.backend.feed.batch.import

import com.mobilispect.backend.feed.gtfs.ParsedGtfsData
import com.mobilispect.backend.feed.model.ids.ImportId
import com.mobilispect.backend.feed.service.FeedImportService
import org.slf4j.LoggerFactory
import org.springframework.batch.core.step.StepExecution
import org.springframework.batch.core.annotation.BeforeStep
import org.springframework.batch.core.configuration.annotation.StepScope
import org.springframework.batch.infrastructure.item.Chunk
import org.springframework.batch.infrastructure.item.ItemWriter
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import java.util.UUID

@Component
@StepScope
class FeedImportWriter(
    private val feedImportService: FeedImportService
) : ItemWriter<ParsedGtfsData> {
    private val logger = LoggerFactory.getLogger(FeedImportWriter::class.java)

    @Value("#{jobParameters['importId']}")
    lateinit var importId: String

    private var stepExecution: StepExecution? = null

    @BeforeStep
    fun beforeStep(stepExecution: StepExecution) {
        this.stepExecution = stepExecution
    }

    override fun write(chunk: Chunk<out ParsedGtfsData>) {
        val parsedData = chunk.items.firstOrNull() ?: return
        val importUuid = runCatching { UUID.fromString(importId) }
            .getOrElse { throw IllegalArgumentException("importId job parameter is required", it) }
        val id = ImportId(importUuid)

        runCatching {
            feedImportService.completeImport(id, parsedData)

            // Store parsed data in job execution context for subsequent steps
            stepExecution?.jobExecution?.executionContext?.put("parsedData", parsedData)
            logger.info(
                "Stored parsed data in execution context: {} routes, {} trips, {} stops",
                parsedData.routes.size,
                parsedData.trips.size,
                parsedData.stops.size
            )
        }.onFailure { throwable ->
            logger.error("Feed import failed for {}", id, throwable)
            feedImportService.failImport(id, throwable.message ?: "Import failed")
            throw throwable
        }
    }
}
