package com.mobilispect.backend.feed.model.ids

import java.util.UUID

/**
 * Value class for Feed Import identifiers.
 * Ensures type safety and prevents ID mixups across domain boundaries.
 *
 * Per constitutional Code Quality First requirements (FR-018).
 */
@JvmInline
value class ImportId(val value: UUID) {
    override fun toString(): String = value.toString()

    companion object {
        fun fromString(id: String): ImportId = ImportId(UUID.fromString(id))
        fun random(): ImportId = ImportId(UUID.randomUUID())
    }
}
