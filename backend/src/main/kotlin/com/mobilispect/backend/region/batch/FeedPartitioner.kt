package com.mobilispect.backend.region.batch

import com.mobilispect.backend.feed.FeedApi
import com.mobilispect.backend.feed.model.ImportTriggerType
import com.mobilispect.backend.region.RegionId
import org.slf4j.LoggerFactory
import org.springframework.batch.core.configuration.annotation.StepScope
import org.springframework.batch.core.partition.Partitioner
import org.springframework.batch.infrastructure.item.ExecutionContext
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component

/**
 * Partitioner that creates one partition per active feed in a region.
 *
 * Each partition's ExecutionContext contains:
 * - feedOnestopId: The feed identifier
 * - feedName: Human-readable feed name
 * - partitionIndex: Zero-based index for ordering
 *
 * This enables parallel processing of feeds within a single Spring Batch job, avoiding the overhead
 * of launching separate jobs per feed.
 */
@Component
@StepScope
class FeedPartitioner(
  private val feedApi: FeedApi,
  @Value("#{jobParameters['regionOnestopId']}") private val regionOnestopId: String? = null,
  @Value("#{jobParameters['triggerType']}") private val triggerTypeStr: String? = null,
) : Partitioner {

  private val logger = LoggerFactory.getLogger(FeedPartitioner::class.java)

  companion object {
    const val FEED_ONESTOP_ID_KEY = "feedOnestopId"
    const val FEED_NAME_KEY = "feedName"
    const val PARTITION_INDEX_KEY = "partitionIndex"
    const val TRIGGER_TYPE_KEY = "triggerType"
  }

  override fun partition(gridSize: Int): Map<String, ExecutionContext> {
    val regionId =
      regionOnestopId?.let { RegionId(it) }
        ?: throw IllegalStateException("regionOnestopId job parameter is required")

    val triggerType =
      triggerTypeStr?.let { ImportTriggerType.valueOf(it) } ?: ImportTriggerType.MANUAL

    val feeds = feedApi.findActiveFeedsByRegion(regionId)

    if (feeds.isEmpty()) {
      logger.warn("No active feeds found for region: {}", regionId.value)
      return emptyMap()
    }

    logger.info(
      "Creating {} partitions for {} active feeds in region {}",
      feeds.size,
      feeds.size,
      regionId.value,
    )

    return feeds
      .mapIndexed { index, feed ->
        val partitionKey = "partition$index"
        val context =
          ExecutionContext().apply {
            putString(FEED_ONESTOP_ID_KEY, feed.feedId.value)
            putString(FEED_NAME_KEY, feed.name)
            putInt(PARTITION_INDEX_KEY, index)
            putString(TRIGGER_TYPE_KEY, triggerType.name)
          }
        logger.debug(
          "Created partition {} for feed: {} ({})",
          partitionKey,
          feed.feedId.value,
          feed.name,
        )
        partitionKey to context
      }
      .toMap()
  }
}
