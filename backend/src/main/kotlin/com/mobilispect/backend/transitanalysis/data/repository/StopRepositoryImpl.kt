package com.mobilispect.backend.transitanalysis.data.repository

import com.mobilispect.backend.feed.data.repository.FeedJpaRepository
import com.mobilispect.backend.feed.model.ids.FeedId
import com.mobilispect.backend.transitanalysis.data.mapper.StopMapper
import com.mobilispect.backend.transitanalysis.domain.model.Stop
import com.mobilispect.backend.transitanalysis.domain.model.ids.StopId
import com.mobilispect.backend.transitanalysis.domain.repository.StopRepository
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Repository
import java.time.Instant

/**
 * Implementation of [StopRepository] using JPA repositories and mappers.
 *
 * Converts between domain models (with @JvmInline value class IDs) and data entities (with plain String IDs).
 */
@Repository
class StopRepositoryImpl(
    private val jpaRepository: StopJpaRepository,
    private val feedJpaRepository: FeedJpaRepository,
    private val mapper: StopMapper
) : StopRepository {

    override fun findById(stopId: StopId): Stop? =
        jpaRepository.findById(stopId.value)
            .map { mapper.toDomain(it) }
            .orElse(null)

    override fun findByFeedId(feedId: FeedId, pageable: Pageable): Page<Stop> {
        val feedEntity = feedJpaRepository.findByFeedOnestopId(feedId.value)
            ?: throw IllegalArgumentException("Feed not found: $feedId")
        return jpaRepository.findByFeed(feedEntity, pageable)
            .map { mapper.toDomain(it) }
    }

    override fun findByFeedIdAndActive(feedId: FeedId, pageable: Pageable): Page<Stop> {
        val feedEntity = feedJpaRepository.findByFeedOnestopId(feedId.value)
            ?: throw IllegalArgumentException("Feed not found: $feedId")
        return jpaRepository.findByFeedAndActive(feedEntity, pageable)
            .map { mapper.toDomain(it) }
    }

    override fun findByFeedIdAndGtfsStopId(feedId: FeedId, gtfsStopId: String): Stop? =
        jpaRepository.findByFeedIdAndGtfsStopId(feedId.value, gtfsStopId)
            ?.let { mapper.toDomain(it) }

    override fun findByActive(active: Boolean, pageable: Pageable): Page<Stop> =
        jpaRepository.findByActive(active, pageable)
            .map { mapper.toDomain(it) }

    override fun findByBoundingBox(minLat: Double, minLon: Double, maxLat: Double, maxLon: Double): List<Stop> =
        jpaRepository.findByBoundingBox(minLat, minLon, maxLat, maxLon)
            .map { mapper.toDomain(it) }

    override fun findByUpdatedAtAfter(since: Instant, pageable: Pageable): Page<Stop> =
        jpaRepository.findByUpdatedAtAfter(since, pageable)
            .map { mapper.toDomain(it) }

    override fun findByLastSeenAfter(after: Instant, pageable: Pageable): Page<Stop> =
        jpaRepository.findByLastSeenAfter(after, pageable)
            .map { mapper.toDomain(it) }

    override fun findByFeedIdAndLocationType(feedId: FeedId, locationType: Int, pageable: Pageable): Page<Stop> =
        jpaRepository.findByFeedIdAndLocationType(feedId.value, locationType, pageable)
            .map { mapper.toDomain(it) }

    override fun findStationsByFeedId(feedId: FeedId, pageable: Pageable): Page<Stop> =
        jpaRepository.findStationsByFeedId(feedId.value, pageable)
            .map { mapper.toDomain(it) }

    override fun countByFeedId(feedId: FeedId): Long {
        val feedEntity = feedJpaRepository.findByFeedOnestopId(feedId.value)
            ?: return 0
        return jpaRepository.countByFeed(feedEntity)
    }

    override fun countActiveByFeedId(feedId: FeedId): Long {
        val feedEntity = feedJpaRepository.findByFeedOnestopId(feedId.value)
            ?: return 0
        return jpaRepository.countActiveByFeed(feedEntity)
    }

    override fun existsByFeedIdAndGtfsStopId(feedId: FeedId, gtfsStopId: String): Boolean =
        jpaRepository.existsByFeedIdAndGtfsStopId(feedId.value, gtfsStopId)

    override fun save(stop: Stop): Stop {
        val feedEntity = feedJpaRepository.findByFeedOnestopId(stop.feedId.value)
            ?: throw IllegalArgumentException("Feed not found: ${stop.feedId}")
        val entity = mapper.toEntity(stop, feedEntity)
        val saved = jpaRepository.save(entity)
        return mapper.toDomain(saved)
    }

    override fun deleteById(stopId: StopId) {
        jpaRepository.deleteById(stopId.value)
    }

    override fun findAll(): List<Stop> =
        jpaRepository.findAll().map { mapper.toDomain(it) }
}
