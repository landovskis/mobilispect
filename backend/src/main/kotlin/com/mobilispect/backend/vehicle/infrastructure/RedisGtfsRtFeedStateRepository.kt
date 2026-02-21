package com.mobilispect.backend.vehicle.infrastructure

import com.mobilispect.backend.feed.domain.model.ids.FeedId
import com.mobilispect.backend.vehicle.domain.model.GtfsRtFeedState
import com.mobilispect.backend.vehicle.domain.repository.GtfsRtFeedStateRepository
import java.time.Duration
import java.time.Instant
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.stereotype.Repository

/**
 * Redis-based implementation of [GtfsRtFeedStateRepository].
 *
 * Stores feed state in Redis with automatic TTL-based expiration. Suitable for production
 * deployments with multiple instances.
 *
 * Configuration:
 * - Set `gtfsrt.state.storage=redis` to enable this implementation
 * - Configure Redis connection via `spring.data.redis.*` properties
 */
@Repository
@ConditionalOnProperty(name = ["gtfsrt.state.storage"], havingValue = "redis")
class RedisGtfsRtFeedStateRepository(
  private val redisTemplate: RedisTemplate<String, String>,
  private val ttl: Duration = Duration.ofHours(24),
) : GtfsRtFeedStateRepository {

  private val hashOps
    get() = redisTemplate.opsForHash<String, String>()

  companion object {
    private const val KEY_PREFIX = "gtfsrt:state:"
    private const val FIELD_CONTENT_HASH = "contentHash"
    private const val FIELD_ETAG = "etag"
    private const val FIELD_LAST_MODIFIED = "lastModified"
    private const val FIELD_GTFS_RT_TIMESTAMP = "gtfsRtTimestamp"
    private const val FIELD_LAST_FETCHED_AT = "lastFetchedAt"
    private const val FIELD_LAST_PROCESSED_AT = "lastProcessedAt"
  }

  override fun findByFeedId(feedId: FeedId): GtfsRtFeedState? {
    val key = buildKey(feedId)
    val entries = hashOps.entries(key)

    if (entries.isEmpty()) return null

    return GtfsRtFeedState(
      feedId = feedId,
      contentHash = entries[FIELD_CONTENT_HASH],
      etag = entries[FIELD_ETAG],
      lastModified = entries[FIELD_LAST_MODIFIED],
      gtfsRtTimestamp = entries[FIELD_GTFS_RT_TIMESTAMP]?.toLongOrNull(),
      lastFetchedAt = entries[FIELD_LAST_FETCHED_AT]?.let { Instant.parse(it) } ?: Instant.now(),
      lastProcessedAt = entries[FIELD_LAST_PROCESSED_AT]?.let { Instant.parse(it) },
    )
  }

  override fun save(state: GtfsRtFeedState): GtfsRtFeedState {
    val key = buildKey(state.feedId)
    val entries = mutableMapOf<String, String>()

    state.contentHash?.let { entries[FIELD_CONTENT_HASH] = it }
    state.etag?.let { entries[FIELD_ETAG] = it }
    state.lastModified?.let { entries[FIELD_LAST_MODIFIED] = it }
    state.gtfsRtTimestamp?.let { entries[FIELD_GTFS_RT_TIMESTAMP] = it.toString() }
    entries[FIELD_LAST_FETCHED_AT] = state.lastFetchedAt.toString()
    state.lastProcessedAt?.let { entries[FIELD_LAST_PROCESSED_AT] = it.toString() }

    hashOps.putAll(key, entries)
    redisTemplate.expire(key, ttl)
    return state
  }

  override fun deleteByFeedId(feedId: FeedId) {
    redisTemplate.delete(buildKey(feedId))
  }

  /** Find all stored feed states. */
  fun findAll(): List<GtfsRtFeedState> {
    val keys = redisTemplate.keys("$KEY_PREFIX*") ?: emptySet()
    return keys.mapNotNull { key ->
      val feedIdValue = key.removePrefix(KEY_PREFIX)
      findByFeedId(FeedId(feedIdValue))
    }
  }

  private fun buildKey(feedId: FeedId): String = "$KEY_PREFIX${feedId.value}"
}
