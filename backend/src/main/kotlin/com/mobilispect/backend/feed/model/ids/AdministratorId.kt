package com.mobilispect.backend.feed.model.ids

import java.util.UUID

/**
 * Value class for Administrator identifiers.
 * Ensures type safety and prevents ID mixups across domain boundaries.
 *
 * Per constitutional Code Quality First requirements (FR-018).
 */
@JvmInline
value class AdministratorId(val value: UUID) {
    override fun toString(): String = value.toString()

    companion object {
        fun fromString(id: String): AdministratorId = AdministratorId(UUID.fromString(id))
        fun random(): AdministratorId = AdministratorId(UUID.randomUUID())
    }
}
