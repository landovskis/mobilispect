package com.mobilispect.backend.transitanalysis.domain.repository

import com.mobilispect.backend.transitanalysis.domain.model.CommonSectionVariant
import com.mobilispect.backend.transitanalysis.domain.model.ids.VariantHash
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import java.util.UUID

@Repository
interface CommonSectionVariantRepository : JpaRepository<CommonSectionVariant, UUID> {
    @Query("SELECT csv FROM CommonSectionVariant csv WHERE csv.variant.id = :variantId")
    fun findByVariantId(@Param("variantId") variantId: VariantHash): List<CommonSectionVariant>

    @Query("SELECT csv FROM CommonSectionVariant csv WHERE csv.commonSection.id = :sectionId")
    fun findBySectionId(@Param("sectionId") sectionId: UUID): List<CommonSectionVariant>
}
