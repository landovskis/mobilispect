package com.mobilispect.backend.feed.data.repository

import com.mobilispect.backend.feed.data.entity.FeedEntity
import com.mobilispect.backend.feed.model.FeedSpecType
import com.mobilispect.backend.feed.model.FeedStatus
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

/**
 * JPA repository for [FeedEntity] data layer.
 *
 * Provides data access for feed entities using plain String IDs.
 * Used by domain repository implementations to persist and retrieve domain models.
 */
interface FeedJpaRepository : JpaRepository<FeedEntity, String> {

    /**
     * Find feed by its Onestop ID.
     */
    @Query("SELECT f FROM FeedDataEntity f WHERE f.feedOnestopId = :feedId")
    fun findByFeedOnestopId(@Param("feedId") feedId: String): FeedEntity?

    /**
     * Find all feeds associated with a specific region.
     */
    @Query("SELECT f FROM FeedDataEntity f JOIN f.regions r WHERE r.regionOnestopId = :regionId")
    fun findByRegionId(@Param("regionId") regionId: String): List<FeedEntity>

    /**
     * Find feeds by region and status.
     */
    @Query("SELECT f FROM FeedDataEntity f JOIN f.regions r WHERE r.regionOnestopId = :regionId AND f.status IN :statuses")
    fun findByRegionIdAndStatusIn(
        @Param("regionId") regionId: String,
        @Param("statuses") statuses: Collection<FeedStatus>
    ): List<FeedEntity>

    /**
     * Find feeds by region and spec type.
     */
    @Query("SELECT f FROM FeedDataEntity f JOIN f.regions r WHERE r.regionOnestopId = :regionId AND f.specType IN :specTypes")
    fun findByRegionIdAndSpecTypeIn(
        @Param("regionId") regionId: String,
        @Param("specTypes") specTypes: Collection<FeedSpecType>
    ): List<FeedEntity>

    /**
     * Count feeds for a specific region.
     */
    @Query("SELECT COUNT(f) FROM FeedDataEntity f JOIN f.regions r WHERE r.regionOnestopId = :regionId")
    fun countByRegionId(@Param("regionId") regionId: String): Long
}
