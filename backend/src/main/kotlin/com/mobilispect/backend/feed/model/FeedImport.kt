package com.mobilispect.backend.feed.model

import jakarta.persistence.Column
import jakarta.persistence.Convert
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.GeneratedValue
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.OneToMany
import jakarta.persistence.PrePersist
import jakarta.persistence.PreUpdate
import jakarta.persistence.Table
import org.hibernate.annotations.ColumnTransformer
import org.hibernate.annotations.UuidGenerator
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "feed_imports")
class FeedImport(
    @Id
    @GeneratedValue
    @UuidGenerator
    @Column(columnDefinition = "uuid")
    var id: UUID? = null,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "feed_onestop_id", nullable = false)
    var feed: FeedEntity? = null,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "administrator_id")
    var administrator: Administrator? = null,

    @Convert(converter = ImportTriggerTypeConverter::class)
    @ColumnTransformer(read = "trigger_type::text", write = "?::import_trigger_type")
    @Column(name = "trigger_type", nullable = false, length = 16, columnDefinition = "import_trigger_type")
    var triggerType: ImportTriggerType = ImportTriggerType.MANUAL,

    @Convert(converter = ImportStatusConverter::class)
    @ColumnTransformer(read = "status::text", write = "?::import_status")
    @Column(name = "status", nullable = false, length = 16, columnDefinition = "import_status")
    var status: ImportStatus = ImportStatus.PENDING,

    @Column(name = "version_sha1", length = 40)
    var versionSha1: String? = null,

    @Column(name = "started_at")
    var startedAt: Instant? = null,

    @Column(name = "completed_at")
    var completedAt: Instant? = null,

    @Column(name = "file_size_bytes")
    var fileSizeBytes: Long? = null,

    @Column(name = "error_message", columnDefinition = "text")
    var errorMessage: String? = null,

    @Column(name = "created_at", nullable = false)
    var createdAt: Instant = Instant.now(),

    @Column(name = "updated_at", nullable = false)
    var updatedAt: Instant = Instant.now()
) {
    @OneToMany(mappedBy = "feedImport", fetch = FetchType.LAZY)
    val logs: MutableSet<ImportLog> = mutableSetOf()

    @PrePersist
    fun onCreate() {
        val now = Instant.now()
        createdAt = now
        updatedAt = now
        startedAt = startedAt ?: now
    }

    @PreUpdate
    fun onUpdate() {
        updatedAt = Instant.now()
    }
}
