package com.mobilispect.backend.agency.domain.model.converters

import com.mobilispect.backend.agency.domain.model.ids.AgencyId
import jakarta.persistence.AttributeConverter
import jakarta.persistence.Converter

/**
 * JPA AttributeConverter for AgencyId value class.
 *
 * Converts between the AgencyId value class and its underlying String representation
 * for database persistence and ID lookup operations. autoApply is disabled to
 * avoid implicit conversion on identifier fields in Hibernate 7.
 *
 * Per constitutional Code Quality First requirements (FR-018) for value classes.
 */
@Converter(autoApply = false)
class AgencyIdConverter : AttributeConverter<AgencyId, String> {

    override fun convertToDatabaseColumn(attribute: AgencyId?): String? =
        attribute?.value

    override fun convertToEntityAttribute(dbData: String?): AgencyId? =
        dbData?.let { AgencyId(it) }
}
