package com.mobilispect.backend.feed.repository

import com.mobilispect.backend.feed.model.FeedEntity
import com.mobilispect.backend.feed.model.FeedSpecType
import com.mobilispect.backend.feed.model.FeedStatus
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository

@Repository("feedManagementFeedRepository")
interface FeedRepository : JpaRepository<FeedEntity, String> {

    @Query("SELECT f FROM FeedEntity f JOIN f.regions r WHERE r.regionOnestopId = :regionOnestopId")
    fun findAllByRegionRegionOnestopId(@Param("regionOnestopId") regionOnestopId: String): List<FeedEntity>

    @Query("SELECT COUNT(f) FROM FeedEntity f JOIN f.regions r WHERE r.regionOnestopId = :regionOnestopId")
    fun countByRegionRegionOnestopId(@Param("regionOnestopId") regionOnestopId: String): Long

    @Query("SELECT f FROM FeedEntity f JOIN f.regions r WHERE r.regionOnestopId = :regionOnestopId AND f.status IN :statuses")
    fun findAllByRegionRegionOnestopIdAndStatusIn(
        @Param("regionOnestopId") regionOnestopId: String,
        @Param("statuses") statuses: Collection<FeedStatus>
    ): List<FeedEntity>

    @Query("SELECT f FROM FeedEntity f JOIN f.regions r WHERE r.regionOnestopId = :regionOnestopId AND f.specType IN :specTypes")
    fun findAllByRegionRegionOnestopIdAndSpecTypeIn(
        @Param("regionOnestopId") regionOnestopId: String,
        @Param("specTypes") specTypes: Collection<FeedSpecType>
    ): List<FeedEntity>
}
