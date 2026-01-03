package com.mobilispect.backend.feed.model.ids

import jakarta.persistence.AttributeConverter
import jakarta.persistence.Converter
import java.util.UUID

/**
 * JPA converter for ImportId value class. Converts between ImportId (value class) and UUID
 * (database type). autoApply is disabled to avoid implicit conversion on identifier fields in
 * Hibernate 7.
 *
 * Per constitutional Code Quality First requirements (FR-018).
 */
@Converter(autoApply = false)
class ImportIdConverter : AttributeConverter<ImportId, UUID> {

  override fun convertToDatabaseColumn(attribute: ImportId?): UUID? = attribute?.value

  override fun convertToEntityAttribute(dbData: UUID?): ImportId? = dbData?.let { ImportId(it) }
}
