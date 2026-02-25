package com.mobilispect.backend.feed.data.repository

import com.mobilispect.backend.feed.data.mapper.FeedMapper
import com.mobilispect.backend.feed.domain.model.Feed
import com.mobilispect.backend.feed.domain.model.ids.FeedId
import com.mobilispect.backend.feed.domain.repository.FeedRepository
import com.mobilispect.backend.feed.model.FeedSpecType
import com.mobilispect.backend.feed.model.FeedStatus
import com.mobilispect.backend.region.RegionId
import com.mobilispect.backend.region.data.repository.MetropolitanRegionJpaRepository
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional

/**
 * Implementation of [FeedRepository] using JPA repositories and mappers.
 *
 * Converts between domain models (with @JvmInline value class IDs) and data entities (with plain
 * String IDs). Manages many-to-many relationship with regions through junction table per ADR 0009
 * FK-only pattern.
 */
@Repository("feedDomainRepository")
class FeedRepositoryImpl(
  private val jpaRepository: FeedJpaRepository,
  private val regionJpaRepository: MetropolitanRegionJpaRepository,
  private val mapper: FeedMapper,
) : FeedRepository {

  @Transactional(readOnly = true)
  override fun findById(feedId: FeedId): Feed? =
    jpaRepository.findByFeedOnestopId(feedId.value)?.let { mapper.toDomain(it) }

  @Transactional(readOnly = true)
  override fun findByRegionId(regionId: RegionId): List<Feed> =
    jpaRepository.findByRegionId(regionId.value).map { mapper.toDomain(it) }

  @Transactional(readOnly = true)
  override fun findByRegionIdAndStatusIn(
    regionId: RegionId,
    statuses: Collection<FeedStatus>,
  ): List<Feed> =
    jpaRepository
      .findByRegionIdAndStatusIn(
        regionId.value,
        statuses.first().dbValue, // Pass string value for PostgreSQL enum casting
      )
      .map { mapper.toDomain(it) }

  @Transactional(readOnly = true)
  override fun findByRegionIdAndSpecTypeIn(
    regionId: RegionId,
    specTypes: Collection<FeedSpecType>,
  ): List<Feed> =
    jpaRepository.findByRegionIdAndSpecTypeIn(regionId.value, specTypes).map { mapper.toDomain(it) }

  override fun countByRegionId(regionId: RegionId): Long =
    jpaRepository.countByRegionId(regionId.value)

  @Transactional
  override fun save(feed: Feed): Feed {
    // Convert domain to entity
    val entity = mapper.toEntity(feed)

    // Manage many-to-many relationship with regions (FK-only pattern)
    // Clear existing relationships and rebuild from domain model's regionIds
    entity.regions.clear()
    feed.regionIds.forEach { regionId ->
      regionJpaRepository.findById(regionId.value).ifPresent { region ->
        entity.regions.add(region)
      }
    }

    // Save entity and convert back to domain
    val saved = jpaRepository.save(entity)
    return mapper.toDomain(saved)
  }

  @Transactional
  override fun deleteById(feedId: FeedId) {
    jpaRepository.deleteById(feedId.value)
  }

  @Transactional(readOnly = true)
  override fun findAll(): List<Feed> = jpaRepository.findAll().map { mapper.toDomain(it) }

  @Transactional(readOnly = true)
  override fun findByStatusAndRealtimeFeedUrlNotNull(status: FeedStatus): List<Feed> =
    jpaRepository.findByStatusAndRealtimeFeedUrlNotNull(status.dbValue).map { mapper.toDomain(it) }
}
