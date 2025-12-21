package com.mobilispect.backend.transitanalysis.domain.model.converters

import com.mobilispect.backend.transitanalysis.domain.model.ids.RouteId
import jakarta.persistence.AttributeConverter
import jakarta.persistence.Converter

/**
 * JPA AttributeConverter for RouteId value class.
 *
 * Converts between the RouteId value class and its underlying String representation
 * for database persistence and ID lookup operations. autoApply is disabled to
 * avoid implicit conversion on identifier fields in Hibernate 7.
 *
 * Per constitutional Code Quality First requirements (FR-018) for value classes.
 */
@Converter(autoApply = false)
class RouteIdConverter : AttributeConverter<RouteId, String> {

    override fun convertToDatabaseColumn(attribute: RouteId?): String? =
        attribute?.value

    override fun convertToEntityAttribute(dbData: String?): RouteId? =
        dbData?.let { RouteId(it) }
}
