package com.mobilispect.backend.feed.model.ids

import jakarta.persistence.Embeddable
import java.io.Serializable
import java.util.UUID

/**
 * Value class for Feed Import identifiers. Ensures type safety and prevents ID mixups across domain
 * boundaries.
 *
 * Per constitutional Code Quality First requirements (FR-018).
 *
 * Note: Not using @JvmInline due to Hibernate 7 incompatibility with AttributeConverter on @Id
 * fields. Using @Embeddable for proper Hibernate 7 mapping.
 */
@Embeddable
data class ImportId(val value: UUID = UUID.randomUUID()) : Serializable {
  override fun toString(): String = value.toString()

  companion object {
    fun fromString(id: String): ImportId = ImportId(UUID.fromString(id))

    fun random(): ImportId = ImportId(UUID.randomUUID())
  }
}
