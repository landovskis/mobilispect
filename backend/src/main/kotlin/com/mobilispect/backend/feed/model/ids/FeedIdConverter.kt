package com.mobilispect.backend.feed.model.ids

import jakarta.persistence.AttributeConverter
import jakarta.persistence.Converter

/**
 * JPA AttributeConverter for FeedId value class.
 *
 * Converts between the FeedId value class and its underlying String representation
 * for database persistence and ID lookup operations. The autoApply=true ensures
 * Hibernate automatically uses this converter for all FeedId fields.
 *
 * Per constitutional Code Quality First requirements (FR-018) for value classes.
 */
@Converter(autoApply = true)
class FeedIdConverter : AttributeConverter<FeedId, String> {

    override fun convertToDatabaseColumn(attribute: FeedId?): String? =
        attribute?.value

    override fun convertToEntityAttribute(dbData: String?): FeedId? =
        dbData?.let { FeedId(it) }
}
