package com.mobilispect.backend.feed.repository

import com.mobilispect.backend.feed.model.FeedImport
import com.mobilispect.backend.feed.model.ImportStatus
import com.mobilispect.backend.feed.model.ImportTriggerType
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.util.UUID

@Repository
interface FeedImportRepository : JpaRepository<FeedImport, UUID> {
    fun findAllByFeedFeedOnestopIdOrderByStartedAtDesc(
        feedOnestopId: String,
        pageable: Pageable
    ): Page<FeedImport>

    fun findAllByStatusIn(statuses: Collection<ImportStatus>, pageable: Pageable): Page<FeedImport>

    fun findAllByStatusIn(statuses: Collection<ImportStatus>): List<FeedImport>

    fun findAllByStatusInAndTriggerTypeIn(
        statuses: Collection<ImportStatus>,
        triggerTypes: Collection<ImportTriggerType>,
        pageable: Pageable
    ): Page<FeedImport>

    fun findAllByFeedFeedOnestopIdAndStatusInOrderByStartedAtDesc(
        feedOnestopId: String,
        statuses: Collection<ImportStatus>,
        pageable: Pageable
    ): Page<FeedImport>

    fun findAllByTriggerTypeIn(
        triggerTypes: Collection<ImportTriggerType>,
        pageable: Pageable
    ): Page<FeedImport>
}
