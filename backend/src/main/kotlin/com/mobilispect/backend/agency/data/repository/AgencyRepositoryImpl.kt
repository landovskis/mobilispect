package com.mobilispect.backend.agency.data.repository

import com.mobilispect.backend.agency.data.mapper.AgencyMapper
import com.mobilispect.backend.agency.domain.model.Agency
import com.mobilispect.backend.agency.domain.model.ids.AgencyId
import com.mobilispect.backend.agency.domain.repository.AgencyRepository
import com.mobilispect.backend.feed.data.repository.FeedJpaRepository
import com.mobilispect.backend.feed.model.ids.FeedId
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Repository
import java.time.Instant

/**
 * Implementation of [AgencyRepository] using JPA repositories and mappers.
 *
 * Converts between domain models (with @JvmInline value class IDs) and data entities (with plain String IDs).
 */
@Repository
class AgencyRepositoryImpl(
    private val jpaRepository: AgencyJpaRepository,
    private val feedJpaRepository: FeedJpaRepository,
    private val mapper: AgencyMapper
) : AgencyRepository {

    override fun findById(agencyId: AgencyId): Agency? =
        jpaRepository.findById(agencyId.value)
            .map { mapper.toDomain(it) }
            .orElse(null)

    override fun findByFeedId(feedId: FeedId, pageable: Pageable): Page<Agency> {
        val feedEntity = feedJpaRepository.findByFeedOnestopId(feedId.value)
            ?: throw IllegalArgumentException("Feed not found: $feedId")
        return jpaRepository.findByFeed(feedEntity, pageable)
            .map { mapper.toDomain(it) }
    }

    override fun findByFeedIdAndActive(feedId: FeedId, pageable: Pageable): Page<Agency> {
        val feedEntity = feedJpaRepository.findByFeedOnestopId(feedId.value)
            ?: throw IllegalArgumentException("Feed not found: $feedId")
        return jpaRepository.findByFeedAndActive(feedEntity, pageable)
            .map { mapper.toDomain(it) }
    }

    override fun findByFeedIdAndGtfsAgencyId(feedId: FeedId, gtfsAgencyId: String): Agency? =
        jpaRepository.findByFeedIdAndGtfsAgencyId(feedId.value, gtfsAgencyId)
            ?.let { mapper.toDomain(it) }

    override fun findByActive(active: Boolean, pageable: Pageable): Page<Agency> =
        jpaRepository.findByActive(active, pageable)
            .map { mapper.toDomain(it) }

    override fun findByUpdatedAtAfter(since: Instant, pageable: Pageable): Page<Agency> =
        jpaRepository.findByUpdatedAtAfter(since, pageable)
            .map { mapper.toDomain(it) }

    override fun findByLastFeedImportAfter(after: Instant, pageable: Pageable): Page<Agency> =
        jpaRepository.findByLastFeedImportAfter(after, pageable)
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

    override fun existsByFeedIdAndGtfsAgencyId(feedId: FeedId, gtfsAgencyId: String): Boolean =
        jpaRepository.existsByFeedIdAndGtfsAgencyId(feedId.value, gtfsAgencyId)

    override fun save(agency: Agency): Agency {
        val feedEntity = feedJpaRepository.findByFeedOnestopId(agency.feedId.value)
            ?: throw IllegalArgumentException("Feed not found: ${agency.feedId}")
        val entity = mapper.toEntity(agency, feedEntity)
        val saved = jpaRepository.save(entity)
        return mapper.toDomain(saved)
    }

    override fun deleteById(agencyId: AgencyId) {
        jpaRepository.deleteById(agencyId.value)
    }

    override fun findAll(): List<Agency> =
        jpaRepository.findAll().map { mapper.toDomain(it) }
}
