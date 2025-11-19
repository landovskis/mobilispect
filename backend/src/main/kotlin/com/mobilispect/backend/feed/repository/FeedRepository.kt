package com.mobilispect.backend.feed.repository

import com.mobilispect.backend.feed.model.FeedEntity
import com.mobilispect.backend.feed.model.FeedSpecType
import com.mobilispect.backend.feed.model.FeedStatus
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository

import com.mobilispect.backend.feed.model.ids.FeedId
import com.mobilispect.backend.feed.model.ids.RegionId

@Repository("feedManagementFeedRepository")
interface FeedRepository : JpaRepository<FeedEntity, FeedId> {

    @Query("SELECT f FROM FeedEntity f JOIN f.regions r WHERE r.regionOnestopId = :regionOnestopId")
    fun findAllByRegionRegionOnestopId(@Param("regionOnestopId") regionOnestopId: RegionId): List<FeedEntity>

    @Query("SELECT COUNT(f) FROM FeedEntity f JOIN f.regions r WHERE r.regionOnestopId = :regionOnestopId")
    fun countByRegionRegionOnestopId(@Param("regionOnestopId") regionOnestopId: RegionId): Long

    @Query("SELECT f FROM FeedEntity f JOIN f.regions r WHERE r.regionOnestopId = :regionOnestopId AND f.status IN :statuses")
    fun findAllByRegionRegionOnestopIdAndStatusIn(
        @Param("regionOnestopId") regionOnestopId: RegionId,
        @Param("statuses") statuses: Collection<FeedStatus>
    ): List<FeedEntity>

    @Query("SELECT f FROM FeedEntity f JOIN f.regions r WHERE r.regionOnestopId = :regionOnestopId AND f.specType IN :specTypes")
    fun findAllByRegionRegionOnestopIdAndSpecTypeIn(
        @Param("regionOnestopId") regionOnestopId: RegionId,
        @Param("specTypes") specTypes: Collection<FeedSpecType>
    ): List<FeedEntity>
}
