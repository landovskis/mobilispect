package com.mobilispect.backend.feed.repository

import com.mobilispect.backend.feed.model.ImportLog
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import com.mobilispect.backend.feed.model.ids.ImportId
import java.util.UUID

@Repository
interface ImportLogRepository : JpaRepository<ImportLog, UUID> {
    fun findAllByFeedImportIdOrderByCreatedAtAsc(importId: ImportId): List<ImportLog>
}
