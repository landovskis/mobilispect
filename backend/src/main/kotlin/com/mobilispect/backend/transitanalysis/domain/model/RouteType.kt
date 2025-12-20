package com.mobilispect.backend.transitanalysis.domain.model

/**
 * Enum representing GTFS route types as defined by General Transit Feed Specification.
 *
 * Route types are hierarchical indicators of service mode and passenger comfort.
 * They influence UI presentation, scheduling analysis, and accessibility considerations.
 *
 * Reference: https://gtfs.org/documentation/schedule/reference/#routestxt
 *
 * @property gtfsValue The official GTFS numeric route type code
 * @property value Database enum string for persistence
 */
enum class RouteType(val gtfsValue: Int, val value: String) {
    /**
     * Tram, Streetcar, Light rail.
     * Lightweight rail transit typically operated at street level.
     */
    TRAM(0, "TRAM"),

    /**
     * Subway, Metro.
     * Heavy rail transit typically underground with high capacity.
     */
    SUBWAY(1, "SUBWAY"),

    /**
     * Rail, Intercity rail.
     * Regional or intercity rail service.
     */
    RAIL(2, "RAIL"),

    /**
     * Bus.
     * Standard bus service (most common transit mode).
     */
    BUS(3, "BUS"),

    /**
     * Ferry.
     * Water-based passenger service.
     */
    FERRY(4, "FERRY"),

    /**
     * Cable tram.
     * Cable-driven streetcar system.
     */
    CABLE_TRAM(5, "CABLE_TRAM"),

    /**
     * Aerial lift, Suspended cable car.
     * Cable-driven transportation system suspended above ground.
     */
    AERIAL_LIFT(6, "AERIAL_LIFT"),

    /**
     * Funicular.
     * Rail-based system on steep inclines.
     */
    FUNICULAR(7, "FUNICULAR"),

    /**
     * Trolleybus, Trackless trolley.
     * Electric bus powered by overhead lines.
     */
    TROLLEYBUS(11, "TROLLEYBUS"),

    /**
     * Monorail.
     * Single-rail elevated system.
     */
    MONORAIL(12, "MONORAIL");

    companion object {
        /**
         * Retrieves a RouteType by its database value string.
         *
         * @param value The database enum string
         * @return The matching RouteType
         * @throws IllegalArgumentException if value does not match any route type
         */
        fun fromValue(value: String): RouteType {
            return entries.find { it.value == value }
                ?: throw IllegalArgumentException("Unknown RouteType value: $value. Valid values: ${entries.joinToString { it.value }}")
        }

        /**
         * Retrieves a RouteType by its GTFS numeric code.
         *
         * @param gtfsValue The GTFS route type code
         * @return The matching RouteType
         * @throws IllegalArgumentException if gtfsValue does not match any route type
         */
        fun fromGtfsValue(gtfsValue: Int): RouteType {
            return entries.find { it.gtfsValue == gtfsValue }
                ?: throw IllegalArgumentException("Unknown GTFS route type: $gtfsValue. Valid values: ${entries.joinToString { "${it.value}(${it.gtfsValue})" }}")
        }
    }
}
