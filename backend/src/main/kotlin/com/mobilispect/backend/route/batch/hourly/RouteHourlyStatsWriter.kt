package com.mobilispect.backend.route.batch.hourly

import com.mobilispect.backend.route.domain.repository.RouteHourlyStatRepository
import org.slf4j.LoggerFactory
import org.springframework.batch.core.configuration.annotation.StepScope
import org.springframework.batch.infrastructure.item.Chunk
import org.springframework.batch.infrastructure.item.ItemWriter
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

/** Spring Batch ItemWriter that persists route hourly stats to the database. */
@Component
@StepScope
class RouteHourlyStatsWriter(private val routeHourlyStatRepository: RouteHourlyStatRepository) :
  ItemWriter<RouteHourlyStatsBatch> {

  private val logger = LoggerFactory.getLogger(RouteHourlyStatsWriter::class.java)

  @Transactional
  override fun write(chunk: Chunk<out RouteHourlyStatsBatch>) {
    val batches = chunk.items
    if (batches.isEmpty()) {
      return
    }

    val stats = batches.flatMap { it.stats }
    if (stats.isEmpty()) {
      logger.info("No route hourly stats to persist in this chunk")
      return
    }

    val grouped = stats.groupBy { it.routeId to it.serviceDate }
    grouped.forEach { (key, entries) ->
      val (routeId, serviceDate) = key
      routeHourlyStatRepository.deleteByRouteIdAndServiceDate(routeId, serviceDate)
      routeHourlyStatRepository.saveAll(entries)
      logger.info("Saved {} hourly stats for route {} on {}", entries.size, routeId, serviceDate)
    }
  }
}
