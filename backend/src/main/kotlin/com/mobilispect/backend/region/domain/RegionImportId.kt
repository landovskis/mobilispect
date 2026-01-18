package com.mobilispect.backend.region.domain

import jakarta.persistence.Embeddable
import java.io.Serializable
import java.util.UUID

/**
 * Value class for Region Import identifiers. Ensures type safety and prevents ID mixups across
 * domain boundaries.
 *
 * Per constitutional Code Quality First requirements (FR-018).
 *
 * Note: Not using @JvmInline due to Hibernate 7 incompatibility with AttributeConverter on @Id
 * fields. Using @Embeddable for proper Hibernate 7 mapping.
 */
@Embeddable
data class RegionImportId(val value: UUID = UUID.randomUUID()) : Serializable {
  override fun toString(): String = value.toString()

  companion object {
    fun fromString(id: String): RegionImportId = RegionImportId(UUID.fromString(id))

    fun random(): RegionImportId = RegionImportId(UUID.randomUUID())
  }
}
