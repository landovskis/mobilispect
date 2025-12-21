package com.mobilispect.backend.feed.model

import jakarta.persistence.AttributeOverride
import jakarta.persistence.Column
import jakarta.persistence.Convert
import jakarta.persistence.EmbeddedId
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.OneToMany
import jakarta.persistence.PrePersist
import jakarta.persistence.PreUpdate
import jakarta.persistence.Table
import org.hibernate.annotations.ColumnTransformer
import java.time.Instant
import java.util.UUID

import com.mobilispect.backend.feed.model.ids.AdministratorId

@Entity
@Table(name = "administrators")
class Administrator(
    @EmbeddedId
    @AttributeOverride(name = "value", column = Column(name = "id", nullable = false, updatable = false, columnDefinition = "uuid"))
    var id: AdministratorId = AdministratorId.random(),

    @Column(nullable = false, length = 255, unique = true)
    var username: String = "",

    @Column(nullable = false, length = 255, unique = true)
    var email: String = "",

    @Convert(converter = AdminRoleConverter::class)
    @ColumnTransformer(read = "role::text", write = "?::admin_role")
    @Column(nullable = false, length = 32, columnDefinition = "admin_role")
    var role: AdminRole = AdminRole.FEED_VIEWER,

    @Column(nullable = false)
    var active: Boolean = true,

    @Column(name = "last_login_at")
    var lastLoginAt: Instant? = null,

    @Column(name = "created_at", nullable = false)
    var createdAt: Instant = Instant.now(),

    @Column(name = "updated_at", nullable = false)
    var updatedAt: Instant = Instant.now()
) {
    @OneToMany(mappedBy = "administrator", fetch = FetchType.LAZY)
    val imports: MutableSet<FeedImport> = mutableSetOf()

    constructor() : this(
        id = AdministratorId(),
        username = "",
        email = "",
        role = AdminRole.FEED_VIEWER,
        active = true,
        lastLoginAt = null,
        createdAt = Instant.EPOCH,
        updatedAt = Instant.EPOCH
    )

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
