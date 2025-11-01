package com.mobilispect.backend.feed.repository

import com.mobilispect.backend.feed.model.MetropolitanRegion
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface MetropolitanRegionRepository : JpaRepository<MetropolitanRegion, String> {
    fun findAllByAutoUpdateEnabled(autoUpdateEnabled: Boolean): List<MetropolitanRegion>
}
