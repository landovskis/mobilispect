package com.mobilispect.backend.feed.repository

import com.mobilispect.backend.feed.domain.FeedImport
import com.mobilispect.backend.feed.model.ImportStatus
import com.mobilispect.backend.feed.model.ImportTriggerType
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import java.util.Optional

import com.mobilispect.backend.feed.model.ids.ImportId

@Repository
interface FeedImportRepository : JpaRepository<FeedImport, ImportId> {

    /**
     * Find a feed import by its ID.
     *
     * This method is needed because Hibernate's findById doesn't properly convert
     * the ImportId value class for ID lookups. Use this instead of findById(ImportId).
     */
    @Query("SELECT fi FROM FeedImport fi WHERE fi.id = :importId")
    fun findByImportId(@Param("importId") importId: ImportId): Optional<FeedImport>

    fun findAllByFeedIdOrderByStartedAtDesc(
        feedId: String,
        pageable: Pageable
    ): Page<FeedImport>

    fun findAllByStatusIn(statuses: Collection<ImportStatus>, pageable: Pageable): Page<FeedImport>

    fun findAllByStatusIn(statuses: Collection<ImportStatus>): List<FeedImport>

    fun findAllByStatusInAndTriggerTypeIn(
        statuses: Collection<ImportStatus>,
        triggerTypes: Collection<ImportTriggerType>,
        pageable: Pageable
    ): Page<FeedImport>

    fun findAllByFeedIdAndStatusInOrderByStartedAtDesc(
        feedId: String,
        statuses: Collection<ImportStatus>,
        pageable: Pageable
    ): Page<FeedImport>

    fun findAllByTriggerTypeIn(
        triggerTypes: Collection<ImportTriggerType>,
        pageable: Pageable
    ): Page<FeedImport>
}
