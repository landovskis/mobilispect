package com.mobilispect.backend.transitanalysis.domain.model.converters

import com.mobilispect.backend.transitanalysis.domain.model.ids.VariantHash
import jakarta.persistence.AttributeConverter
import jakarta.persistence.Converter

/**
 * JPA AttributeConverter for VariantHash value class.
 *
 * Converts between the VariantHash value class and its underlying String representation
 * for database persistence and ID lookup operations. The autoApply=true ensures
 * Hibernate automatically uses this converter for all VariantHash fields.
 *
 * Per constitutional Code Quality First requirements (FR-018) for value classes.
 */
@Converter(autoApply = true)
class VariantHashConverter : AttributeConverter<VariantHash, String> {

    override fun convertToDatabaseColumn(attribute: VariantHash?): String? =
        attribute?.value

    override fun convertToEntityAttribute(dbData: String?): VariantHash? =
        dbData?.let { VariantHash(it) }
}
