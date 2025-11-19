package com.mobilispect.backend.feed.repository

import com.mobilispect.backend.feed.model.MetropolitanRegion
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

import com.mobilispect.backend.feed.model.ids.RegionId

@Repository
interface MetropolitanRegionRepository : JpaRepository<MetropolitanRegion, RegionId> {
    fun findAllByAutoUpdateEnabled(autoUpdateEnabled: Boolean): List<MetropolitanRegion>
}
