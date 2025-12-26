package com.mobilispect.backend.feed.model.ids

import jakarta.persistence.AttributeConverter
import jakarta.persistence.Converter

/**
 * JPA AttributeConverter for RegionId value class.
 *
 * Converts between the RegionId value class and its underlying String representation
 * for database persistence and ID lookup operations. autoApply is disabled to
 * avoid implicit conversion on identifier fields in Hibernate 7.
 *
 * Per constitutional Code Quality First requirements (FR-018) for value classes.
 */
@Converter(autoApply = false)
class RegionIdConverter : AttributeConverter<RegionId, String> {

    override fun convertToDatabaseColumn(attribute: RegionId?): String? =
        attribute?.value

    override fun convertToEntityAttribute(dbData: String?): RegionId? =
        dbData?.let { RegionId(it) }
}
