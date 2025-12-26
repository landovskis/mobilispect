package com.mobilispect.backend.feed.data.repository

import com.mobilispect.backend.feed.data.entity.FeedEntity
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
}
