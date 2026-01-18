package com.mobilispect.backend.config

import javax.sql.DataSource
import org.slf4j.LoggerFactory
import org.springframework.boot.context.event.ApplicationReadyEvent
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Component

@Component
class BatchDataSourceLogging(private val dataSource: DataSource) {
  private val logger = LoggerFactory.getLogger(BatchDataSourceLogging::class.java)

  @EventListener(ApplicationReadyEvent::class)
  fun logBatchDataSource() {
    runCatching { dataSource.connection.use { it.metaData.url } }
      .onSuccess { url -> logger.info("Spring Batch DataSource URL: {}", url) }
      .onFailure { throwable ->
        logger.warn("Unable to resolve Spring Batch DataSource URL", throwable)
      }
  }
}
