package com.mobilispect.backend.feed.model

import jakarta.persistence.Column
import jakarta.persistence.Convert
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.PrePersist
import jakarta.persistence.PreUpdate
import jakarta.persistence.Table
import jakarta.persistence.Version
import org.hibernate.annotations.ColumnTransformer
import java.time.Instant

@Entity
@Table(name = "feed_authentication")
class FeedAuthentication(
    @Id
    @Column(name = "feed_onestop_id", length = 512)
    val feedOnestopId: String = "",

    @Convert(converter = AuthTypeConverter::class)
    @ColumnTransformer(read = "auth_type::text", write = "?::auth_type")
    @Column(name = "auth_type", nullable = false, length = 15, columnDefinition = "auth_type")
    var authType: AuthType = AuthType.NONE,

    @Column(name = "encrypted_credentials", columnDefinition = "text")
    var encryptedCredentials: String? = null,

    @Column(name = "primary_credential", columnDefinition = "text")
    var primaryCredential: String? = null,

    @Column(name = "secondary_credential", columnDefinition = "text")
    var secondaryCredential: String? = null,

    @Column(name = "header_name", length = 120)
    var headerName: String? = null,

    @Column(name = "auth_parameters", columnDefinition = "text")
    var authParameters: String? = null,

    @Column(name = "is_active", nullable = false)
    var isActive: Boolean = true,

    @Column(name = "last_auth_success")
    var lastAuthSuccess: Instant? = null,

    @Column(name = "last_auth_failure")
    var lastAuthFailure: Instant? = null,

    @Column(name = "failure_count", nullable = false)
    var failureCount: Int = 0,

    @Column(name = "notes", columnDefinition = "text")
    var notes: String? = null,

    @Column(name = "expires_at")
    var expiresAt: Instant? = null,

    @Column(name = "created_at", nullable = false)
    var createdAt: Instant = Instant.now(),

    @Column(name = "updated_at", nullable = false)
    var updatedAt: Instant = Instant.now(),

    @Version
    @Column(name = "version", nullable = false)
    var version: Long = 0
) {
    @PrePersist
    fun onCreate() {
        val now = Instant.now()
        createdAt = now
        updatedAt = now
    }

    @PreUpdate
    fun onUpdate() {
        updatedAt = Instant.now()
    }
}
