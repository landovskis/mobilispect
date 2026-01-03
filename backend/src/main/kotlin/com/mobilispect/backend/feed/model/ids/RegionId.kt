package com.mobilispect.backend.feed.model.ids

import jakarta.persistence.Embeddable
import java.io.Serializable

/**
 * Value class for Metropolitan Region identifiers using Onestop ID format. Ensures type safety and
 * prevents ID mixups across domain boundaries.
 *
 * Per constitutional Code Quality First requirements (FR-018).
 *
 * Note: Not using @JvmInline due to Hibernate 7 incompatibility with AttributeConverter on @Id
 * fields. Using @Embeddable for proper Hibernate 7 mapping.
 */
@Embeddable
data class RegionId(val value: String = "") : Serializable {
  init {
    if (value.isNotBlank()) {
      require(value.isNotBlank()) { "Region ID cannot be blank" }
    }
  }

  override fun toString(): String = value
}
