package com.mobilispect.backend.feed.data.entity

import com.mobilispect.backend.feed.model.AuthType
import jakarta.persistence.*
import java.time.Instant

/** JPA entity for feed authentication persistence. */
@Entity
@Table(name = "feed_authentication")
class FeedAuthenticationEntity(
  @Id @Column(name = "feed_onestop_id", nullable = false, length = 512) val feedOnestopId: String,
  @OneToOne(fetch = FetchType.LAZY)
  @MapsId
  @JoinColumn(name = "feed_onestop_id")
  val feed: FeedEntity,
  @Column(name = "auth_type", nullable = false, length = 16) var authType: AuthType,
  @Column(name = "encrypted_credentials", columnDefinition = "TEXT")
  var encryptedCredentials: String? = null,
  @Column(name = "primary_credential", columnDefinition = "TEXT")
  var primaryCredential: String? = null,
  @Column(name = "secondary_credential", columnDefinition = "TEXT")
  var secondaryCredential: String? = null,
  @Column(name = "header_name", length = 120) var headerName: String? = null,
  @Column(name = "auth_parameters", columnDefinition = "TEXT") var authParameters: String? = null,
  @Column(name = "is_active", nullable = false) var isActive: Boolean = true,
  @Column(name = "last_auth_success") var lastAuthSuccess: Instant? = null,
  @Column(name = "last_auth_failure") var lastAuthFailure: Instant? = null,
  @Column(name = "failure_count", nullable = false) var failureCount: Int = 0,
  @Column(name = "notes", columnDefinition = "TEXT") var notes: String? = null,
  @Column(name = "expires_at") var expiresAt: Instant? = null,
  @Column(name = "created_at", nullable = false) var createdAt: Instant = Instant.now(),
  @Column(name = "updated_at", nullable = false) var updatedAt: Instant = Instant.now(),
  @Version @Column(name = "version", nullable = false) var version: Long = 0,
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
