package com.mobilispect.backend.persistence

import jakarta.persistence.AttributeConverter

/**
 * Shared AttributeConverter for persisting Kotlin enums into PostgreSQL enum columns.
 *
 * Each entity property declares a {@code @ColumnTransformer} that casts the bound value to the
 * correct PostgreSQL enum type. The converter translates between the Kotlin enum and the database
 * literal (e.g., "gtfs") while keeping the entity field strongly typed.
 */
abstract class PostgreSqlEnumConverter<T : Enum<T>>(
  private val toDbValue: (T) -> String,
  private val fromDbValue: (String?) -> T?,
) : AttributeConverter<T, String> {

  override fun convertToDatabaseColumn(attribute: T?): String? = attribute?.let(toDbValue)

  override fun convertToEntityAttribute(dbData: String?): T? = fromDbValue(dbData)
}
