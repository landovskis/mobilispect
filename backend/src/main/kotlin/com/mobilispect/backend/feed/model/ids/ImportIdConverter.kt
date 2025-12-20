package com.mobilispect.backend.feed.model.ids

import jakarta.persistence.AttributeConverter
import jakarta.persistence.Converter
import java.util.UUID

/**
 * JPA converter for ImportId value class.
 * Converts between ImportId (value class) and UUID (database type).
 *
 * Per constitutional Code Quality First requirements (FR-018).
 */
@Converter(autoApply = true)
class ImportIdConverter : AttributeConverter<ImportId, UUID> {

    override fun convertToDatabaseColumn(attribute: ImportId?): UUID? =
        attribute?.value

    override fun convertToEntityAttribute(dbData: UUID?): ImportId? =
        dbData?.let { ImportId(it) }
}
