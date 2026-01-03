package com.mobilispect.backend.route.domain.repository

import com.mobilispect.backend.route.domain.model.CommonSectionVariant
import java.util.UUID
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository

@Repository
interface CommonSectionVariantRepository : JpaRepository<CommonSectionVariant, UUID> {
  @Query("SELECT csv FROM CommonSectionVariant csv WHERE csv.variantId = :variantId")
  fun findByVariantId(@Param("variantId") variantId: String): List<CommonSectionVariant>

  @Query("SELECT csv FROM CommonSectionVariant csv WHERE csv.commonSection.id = :sectionId")
  fun findBySectionId(@Param("sectionId") sectionId: UUID): List<CommonSectionVariant>
}
