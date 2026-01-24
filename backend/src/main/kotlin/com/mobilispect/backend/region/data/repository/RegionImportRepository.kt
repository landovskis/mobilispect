package com.mobilispect.backend.region.data.repository

import com.mobilispect.backend.region.domain.RegionImport
import com.mobilispect.backend.region.domain.RegionImportId
import com.mobilispect.backend.region.domain.RegionImportStatus
import java.util.Optional
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository

@Repository
interface RegionImportRepository : JpaRepository<RegionImport, RegionImportId> {

  /**
   * Find a region import by its ID.
   *
   * This method is needed because Hibernate's findById doesn't properly convert the RegionImportId
   * value class for ID lookups. Use this instead of findById(RegionImportId).
   */
  @Query("SELECT ri FROM RegionImport ri WHERE ri.id = :importId")
  fun findByImportId(@Param("importId") importId: RegionImportId): Optional<RegionImport>

  /** Find all region imports for a specific region, ordered by creation time descending. */
  fun findAllByRegionOnestopIdOrderByCreatedAtDesc(
    regionOnestopId: String,
    pageable: Pageable,
  ): Page<RegionImport>

  /** Find all region imports with one of the given statuses. */
  fun findAllByStatusIn(statuses: Collection<RegionImportStatus>): List<RegionImport>

  /** Find all region imports with one of the given statuses (paginated). */
  fun findAllByStatusIn(
    statuses: Collection<RegionImportStatus>,
    pageable: Pageable,
  ): Page<RegionImport>

  /**
   * Find the active region import for a specific region (if any). Returns at most one result due to
   * the unique partial index on the table.
   */
  @Query(
    """
    SELECT ri FROM RegionImport ri
    WHERE ri.regionOnestopId = :regionOnestopId
    AND ri.status IN :statuses
    """
  )
  fun findActiveByRegionOnestopId(
    @Param("regionOnestopId") regionOnestopId: String,
    @Param("statuses") statuses: Collection<RegionImportStatus>,
  ): Optional<RegionImport>

  /**
   * Check if there is an active import for the given region. More efficient than findActive when
   * you only need to check existence.
   */
  @Query(
    """
    SELECT CASE WHEN COUNT(ri) > 0 THEN true ELSE false END
    FROM RegionImport ri
    WHERE ri.regionOnestopId = :regionOnestopId
    AND ri.status IN :statuses
    """
  )
  fun existsActiveByRegionOnestopId(
    @Param("regionOnestopId") regionOnestopId: String,
    @Param("statuses") statuses: Collection<RegionImportStatus>,
  ): Boolean

  /** Find all active (pending or running) region imports. */
  fun findAllByStatusInOrderByCreatedAtAsc(
    statuses: Collection<RegionImportStatus>
  ): List<RegionImport>

  /** Count the number of region imports with the given status. */
  fun countByStatus(status: RegionImportStatus): Long
}
