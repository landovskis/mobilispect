package com.mobilispect.backend.transitanalysis.domain.model.converters

import com.mobilispect.backend.transitanalysis.domain.model.RouteType
import jakarta.persistence.AttributeConverter
import jakarta.persistence.Converter

/**
 * JPA AttributeConverter for RouteType enum to PostgreSQL custom enum type.
 *
 * Converts between the Kotlin RouteType enum and its PostgreSQL route_type enum representation.
 * This converter ensures proper type casting when persisting to PostgreSQL's custom enum type.
 *
 * The autoApply=true ensures Hibernate automatically uses this converter for all RouteType fields.
 */
@Converter(autoApply = true)
class RouteTypeConverter : AttributeConverter<RouteType, String> {

    override fun convertToDatabaseColumn(attribute: RouteType?): String? =
        attribute?.value

    override fun convertToEntityAttribute(dbData: String?): RouteType? =
        dbData?.let { RouteType.fromValue(it) }
}
