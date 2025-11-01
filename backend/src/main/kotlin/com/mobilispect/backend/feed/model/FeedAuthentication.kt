package com.mobilispect.backend.feed.model

import jakarta.persistence.Column
import jakarta.persistence.Convert
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.MapsId
import jakarta.persistence.OneToOne
import jakarta.persistence.PrePersist
import jakarta.persistence.PreUpdate
import jakarta.persistence.Table
import org.hibernate.annotations.ColumnTransformer
import java.time.Instant

@Entity
@Table(name = "feed_authentication")
class FeedAuthentication(
    @Id
    @Column(name = "feed_onestop_id", length = 255)
    val feedOnestopId: String = "",

    @Convert(converter = AuthTypeConverter::class)
    @ColumnTransformer(read = "auth_type::text", write = "?::auth_type")
    @Column(name = "auth_type", nullable = false, length = 15, columnDefinition = "auth_type")
    var authType: AuthType = AuthType.NONE,

    @Column(name = "encrypted_credentials", columnDefinition = "text")
    var encryptedCredentials: String? = null,

    @Column(name = "expires_at")
    var expiresAt: Instant? = null,

    @Column(name = "created_at", nullable = false)
    var createdAt: Instant = Instant.now(),

    @Column(name = "updated_at", nullable = false)
    var updatedAt: Instant = Instant.now()
) {
    @OneToOne(fetch = FetchType.LAZY)
    @MapsId
    @JoinColumn(name = "feed_onestop_id")
    lateinit var feed: FeedEntity

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
