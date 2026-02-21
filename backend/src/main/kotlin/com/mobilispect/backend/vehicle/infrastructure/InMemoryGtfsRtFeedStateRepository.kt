package com.mobilispect.backend.vehicle.infrastructure

import com.mobilispect.backend.feed.domain.model.ids.FeedId
import com.mobilispect.backend.vehicle.domain.model.GtfsRtFeedState
import com.mobilispect.backend.vehicle.domain.repository.GtfsRtFeedStateRepository
import java.time.Duration
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Repository

/**
 * In-memory implementation of [GtfsRtFeedStateRepository].
 *
 * Uses a ConcurrentHashMap with time-based eviction. This is the default implementation for
 * development and testing. Production deployments should use Redis for persistence across restarts
 * and horizontal scaling by setting `gtfsrt.state.storage=redis`.
 */
@Repository
@ConditionalOnProperty(
  name = ["gtfsrt.state.storage"],
  havingValue = "memory",
  matchIfMissing = true,
)
class InMemoryGtfsRtFeedStateRepository : GtfsRtFeedStateRepository {

  private val logger = LoggerFactory.getLogger(InMemoryGtfsRtFeedStateRepository::class.java)

  private val store = ConcurrentHashMap<FeedId, GtfsRtFeedState>()
  private val ttl = Duration.ofHours(24)

  override fun findByFeedId(feedId: FeedId): GtfsRtFeedState? {
    val state = store[feedId] ?: return null

    // Check if state has expired
    if (isExpired(state)) {
      store.remove(feedId)
      return null
    }

    return state
  }

  override fun save(state: GtfsRtFeedState): GtfsRtFeedState {
    store[state.feedId] = state
    logger.debug("Saved state for feed {}", state.feedId)
    return state
  }

  override fun deleteByFeedId(feedId: FeedId) {
    store.remove(feedId)
    logger.debug("Deleted state for feed {}", feedId)
  }

  private fun isExpired(state: GtfsRtFeedState): Boolean {
    val expiresAt = state.lastFetchedAt.plus(ttl)
    return Instant.now().isAfter(expiresAt)
  }

  /** Clear all state (for testing). */
  fun clear() {
    store.clear()
  }

  /** Get current size (for testing/monitoring). */
  fun size(): Int = store.size

  /** Evict expired entries. Can be called periodically. */
  fun evictExpired(): Int {
    val now = Instant.now()
    val expiredKeys =
      store.entries
        .filter { (_, state) -> now.isAfter(state.lastFetchedAt.plus(ttl)) }
        .map { it.key }

    expiredKeys.forEach { store.remove(it) }
    if (expiredKeys.isNotEmpty()) {
      logger.debug("Evicted {} expired feed states", expiredKeys.size)
    }
    return expiredKeys.size
  }
}
