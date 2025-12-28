package com.mobilispect.backend.agency.domain.repository

import com.mobilispect.backend.agency.domain.model.Agency
import com.mobilispect.backend.agency.domain.model.ids.AgencyId
import com.mobilispect.backend.feed.api.ids.GTFSAgencyId
import com.mobilispect.backend.feed.domain.model.ids.FeedId
import java.time.Instant
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable

/**
 * Domain repository for [Agency] entities.
 *
 * Provides data access methods for agencies using domain models and type-safe value class IDs.
 * Implementation delegates to JPA repositories and uses mappers for domain/data conversion.
 */
interface AgencyRepository {

  /** Find agency by its Onestop ID. */
  fun findById(agencyId: AgencyId): Agency?

  /** Find all agencies for a specific feed. */
  fun findByFeedId(feedId: FeedId, pageable: Pageable): Page<Agency>

  /** Find all active agencies for a specific feed. */
  fun findByFeedIdAndActive(feedId: FeedId, pageable: Pageable): Page<Agency>

  /** Find agency by feed and GTFS agency ID. */
  fun findByFeedIdAndGtfsAgencyId(feedId: FeedId, gtfsAgencyId: GTFSAgencyId): Agency?

  /** Find all agencies with a specific active status. */
  fun findByActive(active: Boolean, pageable: Pageable): Page<Agency>

  /** Find agencies updated since a specific timestamp. */
  fun findByUpdatedAtAfter(since: Instant, pageable: Pageable): Page<Agency>

  /** Find agencies with recent feed imports. */
  fun findByLastFeedImportAfter(after: Instant, pageable: Pageable): Page<Agency>

  /** Count agencies for a specific feed. */
  fun countByFeedId(feedId: FeedId): Long

  /** Count active agencies for a specific feed. */
  fun countActiveByFeedId(feedId: FeedId): Long

  /** Check if an agency exists for a specific feed and GTFS ID. */
  fun existsByFeedIdAndGtfsAgencyId(feedId: FeedId, gtfsAgencyId: GTFSAgencyId): Boolean

  /** Save an agency. */
  fun save(agency: Agency): Agency

  /** Delete an agency by ID. */
  fun deleteById(agencyId: AgencyId)

  /** Find all agencies. */
  fun findAll(): List<Agency>
}
