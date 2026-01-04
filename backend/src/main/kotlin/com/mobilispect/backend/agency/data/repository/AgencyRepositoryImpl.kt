package com.mobilispect.backend.agency.data.repository

import com.mobilispect.backend.agency.data.mapper.AgencyMapper
import com.mobilispect.backend.agency.domain.model.Agency
import com.mobilispect.backend.agency.domain.model.ids.AgencyId
import com.mobilispect.backend.agency.domain.repository.AgencyRepository
import com.mobilispect.backend.feed.api.FeedQueryApi
import com.mobilispect.backend.feed.api.ids.FeedLocalAgencyId
import com.mobilispect.backend.feed.domain.model.ids.FeedId
import java.time.Instant
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Repository

/**
 * Implementation of [AgencyRepository] using JPA repositories and mappers.
 *
 * Converts between domain models (with @JvmInline value class IDs) and data entities (with plain
 * String IDs).
 */
@Repository
class AgencyRepositoryImpl(
  private val jpaRepository: AgencyJpaRepository,
  private val feedQueryApi: FeedQueryApi,
  private val mapper: AgencyMapper,
) : AgencyRepository {

  override fun findById(id: AgencyId): Agency? =
    jpaRepository.findById(id.value).map { mapper.toDomain(it) }.orElse(null)

  override fun findByFeedId(feedId: FeedId, pageable: Pageable): Page<Agency> {
    // Validate feed exists via API
    feedQueryApi.findFeedById(feedId) ?: throw IllegalArgumentException("Feed not found: $feedId")
    return jpaRepository.findByFeedId(feedId.value, pageable).map { mapper.toDomain(it) }
  }

  override fun findByFeedIdAndActive(feedId: FeedId, pageable: Pageable): Page<Agency> {
    // Validate feed exists via API
    feedQueryApi.findFeedById(feedId) ?: throw IllegalArgumentException("Feed not found: $feedId")
    return jpaRepository.findByFeedIdAndActive(feedId.value, pageable).map { mapper.toDomain(it) }
  }

  override fun findByActive(active: Boolean, pageable: Pageable): Page<Agency> =
    jpaRepository.findByActive(active, pageable).map { mapper.toDomain(it) }

  override fun findByUpdatedAtAfter(since: Instant, pageable: Pageable): Page<Agency> =
    jpaRepository.findByUpdatedAtAfter(since, pageable).map { mapper.toDomain(it) }

  override fun findByLastFeedImportAfter(after: Instant, pageable: Pageable): Page<Agency> =
    jpaRepository.findByLastFeedImportAfter(after, pageable).map { mapper.toDomain(it) }

  override fun countByFeedId(feedId: FeedId): Long {
    // Check if feed exists via API
    feedQueryApi.findFeedById(feedId) ?: return 0
    return jpaRepository.countByFeedId(feedId.value)
  }

  override fun countActiveByFeedId(feedId: FeedId): Long {
    // Check if feed exists via API
    feedQueryApi.findFeedById(feedId) ?: return 0
    return jpaRepository.countActiveByFeedId(feedId.value)
  }

  override fun existsByFeedIdAndGtfsAgencyId(feedId: FeedId, gtfsAgencyId: FeedLocalAgencyId): Boolean =
    jpaRepository.existsByFeedIdAndGtfsAgencyId(feedId.value, gtfsAgencyId.value)

  override fun save(agency: Agency): Agency {
    // Validate feed exists via API
    feedQueryApi.findFeedById(agency.feedId)
      ?: throw IllegalArgumentException("Feed not found: ${agency.feedId}")
    val entity = mapper.toEntity(agency)
    val saved = jpaRepository.save(entity)
    return mapper.toDomain(saved)
  }

  override fun deleteById(agencyId: AgencyId) {
    jpaRepository.deleteById(agencyId.value)
  }

  override fun findAll(): List<Agency> = jpaRepository.findAll().map { mapper.toDomain(it) }
}
