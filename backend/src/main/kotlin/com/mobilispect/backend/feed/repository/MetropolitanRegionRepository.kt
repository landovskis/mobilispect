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
}
