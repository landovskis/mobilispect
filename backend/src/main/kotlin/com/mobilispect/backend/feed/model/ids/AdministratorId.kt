package com.mobilispect.backend.feed.model.ids

import jakarta.persistence.Embeddable
import java.io.Serializable
import java.util.UUID

/**
 * Value class for Administrator identifiers. Ensures type safety and prevents ID mixups across
 * domain boundaries.
 *
 * Per constitutional Code Quality First requirements (FR-018).
 *
 * Note: Not using @JvmInline due to Hibernate 7 incompatibility with AttributeConverter on @Id
 * fields. Using @Embeddable for proper Hibernate 7 mapping.
 */
@Embeddable
data class AdministratorId(val value: UUID = UUID.randomUUID()) : Serializable {
  override fun toString(): String = value.toString()

  companion object {
    fun fromString(id: String): AdministratorId = AdministratorId(UUID.fromString(id))

    fun random(): AdministratorId = AdministratorId(UUID.randomUUID())
  }
}
