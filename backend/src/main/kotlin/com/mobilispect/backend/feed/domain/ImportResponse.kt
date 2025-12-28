package com.mobilispect.backend.feed.domain

import java.time.Instant

/**
 * Import Response
 *
 * Response when starting an import.
 */
data class ImportResponse(
    val id: String,
    val importId: String,
    val feedOnestopId: String,
    val triggerType: TriggerType,
    val status: ImportStatus,
    val versionSha1: String? = null,
    val startedAt: Instant,
    val completedAt: Instant? = null,
    val fileSizeBytes: Long? = null,
    val errorMessage: String? = null,
    val createdAt: Instant,
    val updatedAt: Instant,
    val message: String? = null
)
