package com.mobilispect.backend.region.data.repository

import com.mobilispect.backend.region.data.MetropolitanRegionEntity
import org.springframework.data.jpa.repository.JpaRepository

/**
 * JPA repository for [MetropolitanRegionEntity] data layer.
 *
 * Provides data access for metropolitan region entities using plain String IDs.
 * Used by feed repository implementations to manage feed-region relationships.
 */
interface MetropolitanRegionJpaRepository : JpaRepository<MetropolitanRegionEntity, String>
