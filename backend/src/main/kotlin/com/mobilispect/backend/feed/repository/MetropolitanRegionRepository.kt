package com.mobilispect.backend.feed.repository

import com.mobilispect.backend.region.domain.MetropolitanRegion
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository
import java.util.Optional

import com.mobilispect.backend.feed.model.ids.RegionId

@Repository
interface MetropolitanRegionRepository : JpaRepository<MetropolitanRegion, RegionId> {
    fun findAllByAutoUpdateEnabled(autoUpdateEnabled: Boolean): List<MetropolitanRegion>

    /**
     * Find a region by its Onestop ID string value.
     *
     * This method is needed because Hibernate's findById doesn't properly convert
     * the RegionId value class for ID lookups. Use this instead of findById(RegionId).
     */
    @Query("SELECT r FROM MetropolitanRegion r WHERE r.regionOnestopId = :regionId")
    fun findByRegionOnestopId(regionId: RegionId): Optional<MetropolitanRegion>

    /**
     * Find all regions that have at least one feed with a completed import.
     *
     * This query joins:
     * - MetropolitanRegion -> FeedEntity (via feed_regions many-to-many, using MEMBER OF)
     * - FeedEntity -> FeedImport (via feedOnestopId)
     *
     * DISTINCT is used to avoid duplicate regions when a region has multiple feeds
     * or a feed has multiple completed imports.
     */
    @Query("""
        SELECT DISTINCT r FROM MetropolitanRegion r
        JOIN FeedEntity f ON r MEMBER OF f.regions
        JOIN FeedImport fi ON fi.feedOnestopId = f.feedOnestopId
        WHERE fi.status = com.mobilispect.backend.feed.model.ImportStatus.COMPLETED
    """)
    fun findAllWithCompletedImports(): List<MetropolitanRegion>

    /**
     * Find all regions that have at least one feed with a completed import,
     * filtered by autoUpdateEnabled status.
     *
     * This query combines the completed import filter with the autoUpdateEnabled filter.
     */
    @Query("""
        SELECT DISTINCT r FROM MetropolitanRegion r
        JOIN FeedEntity f ON r MEMBER OF f.regions
        JOIN FeedImport fi ON fi.feedOnestopId = f.feedOnestopId
        WHERE r.autoUpdateEnabled = :autoUpdateEnabled
        AND fi.status = com.mobilispect.backend.feed.model.ImportStatus.COMPLETED
    """)
    fun findAllByAutoUpdateEnabledWithCompletedImports(
        autoUpdateEnabled: Boolean
    ): List<MetropolitanRegion>
}
