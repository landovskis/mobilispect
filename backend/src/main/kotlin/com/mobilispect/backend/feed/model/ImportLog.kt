package com.mobilispect.backend.feed.model

import jakarta.persistence.Column
import jakarta.persistence.Convert
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.GeneratedValue
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.PrePersist
import jakarta.persistence.Table
import org.hibernate.annotations.ColumnTransformer
import org.hibernate.annotations.UuidGenerator
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "import_logs")
class ImportLog(
    @Id
    @GeneratedValue
    @UuidGenerator
    @Column(columnDefinition = "uuid")
    var id: UUID? = null,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "import_id", nullable = false)
    var feedImport: FeedImport? = null,

    @Convert(converter = LogLevelConverter::class)
    @ColumnTransformer(read = "level::text", write = "?::log_level")
    @Column(name = "level", nullable = false, length = 8, columnDefinition = "log_level")
    var level: LogLevel = LogLevel.INFO,

    @Column(name = "message", nullable = false, columnDefinition = "text")
    var message: String = "",

    @Column(name = "component", length = 255)
    var component: String? = null,

    @Column(name = "details", columnDefinition = "jsonb")
    var details: String? = null,

    @Column(name = "created_at", nullable = false)
    var createdAt: Instant = Instant.now()
) {
    @PrePersist
    fun onCreate() {
        createdAt = Instant.now()
    }
}
