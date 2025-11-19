package com.mobilispect.backend.feed.repository

import com.mobilispect.backend.feed.model.FeedImport
import com.mobilispect.backend.feed.model.ImportStatus
import com.mobilispect.backend.feed.model.ImportTriggerType
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.util.UUID

import com.mobilispect.backend.feed.model.ids.ImportId
import com.mobilispect.backend.feed.model.ids.FeedId

@Repository
interface FeedImportRepository : JpaRepository<FeedImport, ImportId> {
    fun findAllByFeedFeedOnestopIdOrderByStartedAtDesc(
        feedOnestopId: FeedId,
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
        feedOnestopId: FeedId,
        statuses: Collection<ImportStatus>,
        pageable: Pageable
    ): Page<FeedImport>

    fun findAllByTriggerTypeIn(
        triggerTypes: Collection<ImportTriggerType>,
        pageable: Pageable
    ): Page<FeedImport>
}
