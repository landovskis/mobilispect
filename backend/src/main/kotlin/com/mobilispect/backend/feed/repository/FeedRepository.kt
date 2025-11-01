package com.mobilispect.backend.feed.repository

import com.mobilispect.backend.feed.model.FeedEntity
import com.mobilispect.backend.feed.model.FeedSpecType
import com.mobilispect.backend.feed.model.FeedStatus
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository("feedManagementFeedRepository")
interface FeedRepository : JpaRepository<FeedEntity, String> {
    fun findAllByRegionRegionOnestopId(regionOnestopId: String): List<FeedEntity>
    fun countByRegionRegionOnestopId(regionOnestopId: String): Long
    fun findAllByRegionRegionOnestopIdAndStatusIn(
        regionOnestopId: String,
        statuses: Collection<FeedStatus>
    ): List<FeedEntity>

    fun findAllByRegionRegionOnestopIdAndSpecTypeIn(
        regionOnestopId: String,
        specTypes: Collection<FeedSpecType>
    ): List<FeedEntity>
}
