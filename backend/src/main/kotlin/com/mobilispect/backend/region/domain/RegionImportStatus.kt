package com.mobilispect.backend.region.domain

import com.mobilispect.backend.persistence.PostgreSqlEnumConverter
import jakarta.persistence.Converter

/**
 * Status enum for region-level bulk imports. Includes PARTIAL_SUCCESS for cases where some feeds
 * succeed and some fail.
 *
 * Note: Uses lowercase database values to match PostgreSQL enum definition in
 * V054__create_region_imports_table.sql
 */
enum class RegionImportStatus(val dbValue: String) {
  PENDING("pending"),
  RUNNING("running"),
  COMPLETED("completed"),
  PARTIAL_SUCCESS("partial_success"),
  FAILED("failed"),
  CANCELLED("cancelled");

  companion object {
    private val byDbValue = entries.associateBy { it.dbValue }

    fun fromDb(value: String?): RegionImportStatus? = value?.let(byDbValue::get)
  }

  /** Returns true if this is a terminal state (no further state changes expected). */
  fun isTerminal(): Boolean = this in setOf(COMPLETED, PARTIAL_SUCCESS, FAILED, CANCELLED)

  /** Returns true if the import is currently active (not yet terminated). */
  fun isActive(): Boolean = this in setOf(PENDING, RUNNING)
}

@Converter(autoApply = true)
class RegionImportStatusConverter :
  PostgreSqlEnumConverter<RegionImportStatus>(
    toDbValue = { it.dbValue },
    fromDbValue = { RegionImportStatus.fromDb(it) },
  )
