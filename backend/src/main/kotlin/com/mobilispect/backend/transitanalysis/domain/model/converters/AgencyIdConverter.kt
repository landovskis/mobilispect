package com.mobilispect.backend.transitanalysis.domain.model.converters

import com.mobilispect.backend.transitanalysis.domain.model.ids.AgencyId
import jakarta.persistence.AttributeConverter
import jakarta.persistence.Converter

/**
 * JPA AttributeConverter for AgencyId value class.
 *
 * Converts between the AgencyId value class and its underlying String representation
 * for database persistence and ID lookup operations. The autoApply=true ensures
 * Hibernate automatically uses this converter for all AgencyId fields.
 *
 * Per constitutional Code Quality First requirements (FR-018) for value classes.
 */
@Converter(autoApply = true)
class AgencyIdConverter : AttributeConverter<AgencyId, String> {

    override fun convertToDatabaseColumn(attribute: AgencyId?): String? =
        attribute?.value

    override fun convertToEntityAttribute(dbData: String?): AgencyId? =
        dbData?.let { AgencyId(it) }
}
