package com.mobilispect.backend.stop.data.repository

import com.mobilispect.backend.feed.FeedApi
import com.mobilispect.backend.feed.domain.model.ids.FeedId
import com.mobilispect.backend.stop.data.mapper.StopMapper
import com.mobilispect.backend.stop.domain.model.Stop
import com.mobilispect.backend.stop.domain.model.ids.StopId
import com.mobilispect.backend.stop.domain.repository.StopRepository
import java.time.Instant
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Repository

/**
 * Implementation of [StopRepository] using JPA repositories and mappers.
 *
 * Converts between domain models (with @JvmInline value class IDs) and data entities (with plain
 * String IDs).
 */
@Repository
class StopRepositoryImpl(
  private val jpaRepository: StopJpaRepository,
  private val feedQueryApi: FeedApi,
  private val mapper: StopMapper,
) : StopRepository {

  override fun findById(stopId: StopId): Stop? =
    jpaRepository.findById(stopId.value).map { mapper.toDomain(it) }.orElse(null)

  override fun findByFeedId(feedId: FeedId, pageable: Pageable): Page<Stop> {
    // Validate feed exists via API
    feedQueryApi.findFeedById(feedId) ?: throw IllegalArgumentException("Feed not found: $feedId")
    return jpaRepository.findByFeedId(feedId.value, pageable).map { mapper.toDomain(it) }
  }

  override fun findByFeedIdAndActive(feedId: FeedId, pageable: Pageable): Page<Stop> {
    // Validate feed exists via API
    feedQueryApi.findFeedById(feedId) ?: throw IllegalArgumentException("Feed not found: $feedId")
    return jpaRepository.findByFeedIdAndActive(feedId.value, pageable).map { mapper.toDomain(it) }
  }

  override fun findByFeedIdAndGtfsStopId(feedId: FeedId, gtfsStopId: String): Stop? =
    jpaRepository.findByFeedIdAndGtfsStopId(feedId.value, gtfsStopId)?.let { mapper.toDomain(it) }

  override fun findByActive(active: Boolean, pageable: Pageable): Page<Stop> =
    jpaRepository.findByActive(active, pageable).map { mapper.toDomain(it) }

  override fun findByBoundingBox(
    minLat: Double,
    minLon: Double,
    maxLat: Double,
    maxLon: Double,
  ): List<Stop> =
    jpaRepository.findByBoundingBox(minLat, minLon, maxLat, maxLon).map { mapper.toDomain(it) }

  override fun findByUpdatedAtAfter(since: Instant, pageable: Pageable): Page<Stop> =
    jpaRepository.findByUpdatedAtAfter(since, pageable).map { mapper.toDomain(it) }

  override fun findByLastSeenAfter(after: Instant, pageable: Pageable): Page<Stop> =
    jpaRepository.findByLastSeenAfter(after, pageable).map { mapper.toDomain(it) }

  override fun findByFeedIdAndLocationType(
    feedId: FeedId,
    locationType: Int,
    pageable: Pageable,
  ): Page<Stop> =
    jpaRepository.findByFeedIdAndLocationType(feedId.value, locationType, pageable).map {
      mapper.toDomain(it)
    }

  override fun findStationsByFeedId(feedId: FeedId, pageable: Pageable): Page<Stop> =
    jpaRepository.findStationsByFeedId(feedId.value, pageable).map { mapper.toDomain(it) }

  override fun countByFeedId(feedId: FeedId): Long {
    // Validate feed exists via API
    feedQueryApi.findFeedById(feedId) ?: throw IllegalArgumentException("Feed not found: $feedId")
    return jpaRepository.countByFeedId(feedId.value)
  }

  override fun countActiveByFeedId(feedId: FeedId): Long {
    // Validate feed exists via API
    feedQueryApi.findFeedById(feedId) ?: throw IllegalArgumentException("Feed not found: $feedId")
    return jpaRepository.countActiveByFeedId(feedId.value)
  }

  override fun existsByFeedIdAndGtfsStopId(feedId: FeedId, gtfsStopId: String): Boolean =
    jpaRepository.existsByFeedIdAndGtfsStopId(feedId.value, gtfsStopId)

  override fun save(stop: Stop): Stop {
    // Validate feed exists via API
    feedQueryApi.findFeedById(stop.feedId)
      ?: throw IllegalArgumentException("Feed not found: ${stop.feedId}")
    val entity = mapper.toEntity(stop)
    val saved = jpaRepository.save(entity)
    return mapper.toDomain(saved)
  }

  override fun deleteById(stopId: StopId) {
    jpaRepository.deleteById(stopId.value)
  }

  override fun findAll(): List<Stop> = jpaRepository.findAll().map { mapper.toDomain(it) }
}
