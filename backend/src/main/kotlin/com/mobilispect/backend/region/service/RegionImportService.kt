package com.mobilispect.backend.region.service

import com.mobilispect.backend.region.RegionId
import com.mobilispect.backend.region.data.repository.RegionImportRepository
import com.mobilispect.backend.region.domain.RegionImport
import com.mobilispect.backend.region.domain.RegionImportId
import com.mobilispect.backend.region.domain.RegionImportStatus
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional

/**
 * Service responsible for managing region-level feed import operations.
 *
 * Provides query and status methods for RegionImport records. Import orchestration is handled
 * externally by Airflow; this service exposes the state written by those external processes via the
 * REST API.
 *
 * Constitutional Requirements:
 * - Module Boundaries: Coordinates between region and feed modules via public APIs
 * - Database Persistence: All state tracked in database for reliability
 */
@Service
class RegionImportService(private val regionImportRepository: RegionImportRepository) {
  private val logger = LoggerFactory.getLogger(RegionImportService::class.java)

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  fun failRegionImport(regionImportId: RegionImportId, message: String) {
    val regionImport =
      regionImportRepository.findByImportId(regionImportId).orElseThrow {
        IllegalArgumentException("Region import not found: $regionImportId")
      }

    regionImport.fail(message)
    regionImportRepository.save(regionImport)
  }

  // ========== Query Methods ==========

  /**
   * Get a region import by its ID.
   *
   * @param regionImportId The region import identifier
   * @return The region import if found, null otherwise
   */
  fun getRegionImport(regionImportId: RegionImportId): RegionImport? {
    return regionImportRepository.findByImportId(regionImportId).orElse(null)
  }

  /**
   * Get the active region import for a specific region (if any).
   *
   * @param regionId The region identifier
   * @return The active region import if one exists, null otherwise
   */
  fun getActiveImportForRegion(regionId: RegionId): RegionImport? {
    val activeStatuses = listOf(RegionImportStatus.PENDING, RegionImportStatus.RUNNING)
    return regionImportRepository
      .findActiveByRegionOnestopId(regionId.value, activeStatuses)
      .orElse(null)
  }

  /**
   * Get all active region imports (pending or running).
   *
   * @return List of active region imports
   */
  fun getActiveRegionImports(): List<RegionImport> {
    return regionImportRepository.findAllByStatusInOrderByCreatedAtAsc(
      listOf(RegionImportStatus.PENDING, RegionImportStatus.RUNNING)
    )
  }
}
