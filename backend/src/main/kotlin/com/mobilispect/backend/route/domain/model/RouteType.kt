package com.mobilispect.backend.route.domain.model

/**
 * Enum representing GTFS route types as defined by General Transit Feed Specification.
 *
 * Route types are hierarchical indicators of service mode and passenger comfort. They influence UI
 * presentation, scheduling analysis, and accessibility considerations.
 *
 * Reference: https://gtfs.org/documentation/schedule/reference/#routestxt
 *
 * @property gtfsValue The official GTFS numeric route type code
 * @property value Database enum string for persistence
 */
enum class RouteType(val gtfsValue: Int, val value: String) {
  /** Tram, Streetcar, Light rail. Lightweight rail transit typically operated at street level. */
  TRAM(0, "TRAM"),

  /** Subway, Metro. Heavy rail transit typically underground with high capacity. */
  SUBWAY(1, "SUBWAY"),

  /** Rail, Intercity rail. Regional or intercity rail service. */
  RAIL(2, "RAIL"),

  /** Bus. Standard bus service (most common transit mode). */
  BUS(3, "BUS"),

  /** Ferry. Water-based passenger service. */
  FERRY(4, "FERRY"),

  /** Cable tram. Cable-driven streetcar system. */
  CABLE_TRAM(5, "CABLE_TRAM"),

  /**
   * Aerial lift, Suspended cable car. Cable-driven transportation system suspended above ground.
   */
  AERIAL_LIFT(6, "AERIAL_LIFT"),

  /** Funicular. Rail-based system on steep inclines. */
  FUNICULAR(7, "FUNICULAR"),

  /** Trolleybus, Trackless trolley. Electric bus powered by overhead lines. */
  TROLLEYBUS(11, "TROLLEYBUS"),

  /** Monorail. Single-rail elevated system. */
  MONORAIL(12, "MONORAIL"),

  /** Railway Service. */
  RAILWAY_SERVICE(100, "RAILWAY_SERVICE"),
  /** High Speed Rail Service. */
  HIGH_SPEED_RAIL_SERVICE(101, "HIGH_SPEED_RAIL_SERVICE"),
  /** Long Distance Trains. */
  LONG_DISTANCE_TRAINS(102, "LONG_DISTANCE_TRAINS"),
  /** Inter Regional Rail Service. */
  INTER_REGIONAL_RAIL_SERVICE(103, "INTER_REGIONAL_RAIL_SERVICE"),
  /** Car Transport Rail Service. */
  CAR_TRANSPORT_RAIL_SERVICE(104, "CAR_TRANSPORT_RAIL_SERVICE"),
  /** Sleeper Rail Service. */
  SLEEPER_RAIL_SERVICE(105, "SLEEPER_RAIL_SERVICE"),
  /** Regional Rail Service. */
  REGIONAL_RAIL_SERVICE(106, "REGIONAL_RAIL_SERVICE"),
  /** Tourist Railway Service. */
  TOURIST_RAILWAY_SERVICE(107, "TOURIST_RAILWAY_SERVICE"),
  /** Rail Shuttle (Within Complex). */
  RAIL_SHUTTLE(108, "RAIL_SHUTTLE"),
  /** Suburban Railway. */
  SUBURBAN_RAILWAY(109, "SUBURBAN_RAILWAY"),
  /** Replacement Rail Service. */
  REPLACEMENT_RAIL_SERVICE(110, "REPLACEMENT_RAIL_SERVICE"),
  /** Special Rail Service. */
  SPECIAL_RAIL_SERVICE(111, "SPECIAL_RAIL_SERVICE"),
  /** Lorry Transport Rail Service. */
  LORRY_TRANSPORT_RAIL_SERVICE(112, "LORRY_TRANSPORT_RAIL_SERVICE"),
  /** All Rail Services. */
  ALL_RAIL_SERVICES(113, "ALL_RAIL_SERVICES"),
  /** Cross-Country Rail Service. */
  CROSS_COUNTRY_RAIL_SERVICE(114, "CROSS_COUNTRY_RAIL_SERVICE"),
  /** Vehicle Transport Rail Service. */
  VEHICLE_TRANSPORT_RAIL_SERVICE(115, "VEHICLE_TRANSPORT_RAIL_SERVICE"),
  /** Rack and Pinion Railway. */
  RACK_AND_PINION_RAILWAY(116, "RACK_AND_PINION_RAILWAY"),
  /** Additional Rail Service. */
  ADDITIONAL_RAIL_SERVICE(117, "ADDITIONAL_RAIL_SERVICE"),

  /** Coach Service. */
  COACH_SERVICE(200, "COACH_SERVICE"),
  /** International Coach Service. */
  INTERNATIONAL_COACH_SERVICE(201, "INTERNATIONAL_COACH_SERVICE"),
  /** National Coach Service. */
  NATIONAL_COACH_SERVICE(202, "NATIONAL_COACH_SERVICE"),
  /** Shuttle Coach Service. */
  SHUTTLE_COACH_SERVICE(203, "SHUTTLE_COACH_SERVICE"),
  /** Regional Coach Service. */
  REGIONAL_COACH_SERVICE(204, "REGIONAL_COACH_SERVICE"),
  /** Special Coach Service. */
  SPECIAL_COACH_SERVICE(205, "SPECIAL_COACH_SERVICE"),
  /** Sightseeing Coach Service. */
  SIGHTSEEING_COACH_SERVICE(206, "SIGHTSEEING_COACH_SERVICE"),
  /** Tourist Coach Service. */
  TOURIST_COACH_SERVICE(207, "TOURIST_COACH_SERVICE"),
  /** Commuter Coach Service. */
  COMMUTER_COACH_SERVICE(208, "COMMUTER_COACH_SERVICE"),
  /** All Coach Services. */
  ALL_COACH_SERVICES(209, "ALL_COACH_SERVICES"),

  /** Urban Railway Service. */
  URBAN_RAILWAY_SERVICE(400, "URBAN_RAILWAY_SERVICE"),
  /** Metro Service. */
  METRO_SERVICE(401, "METRO_SERVICE"),
  /** Underground Service. */
  UNDERGROUND_SERVICE(402, "UNDERGROUND_SERVICE"),
  /** Urban Railway Service (alternate code). */
  URBAN_RAILWAY_SERVICE_403(403, "URBAN_RAILWAY_SERVICE_403"),
  /** All Urban Railway Services. */
  ALL_URBAN_RAILWAY_SERVICES(404, "ALL_URBAN_RAILWAY_SERVICES"),
  /** Monorail (extended). */
  MONORAIL_405(405, "MONORAIL_405"),

  /** Bus Service. */
  BUS_SERVICE(700, "BUS_SERVICE"),
  /** Regional Bus Service. */
  REGIONAL_BUS_SERVICE(701, "REGIONAL_BUS_SERVICE"),
  /** Express Bus Service. */
  EXPRESS_BUS_SERVICE(702, "EXPRESS_BUS_SERVICE"),
  /** Stopping Bus Service. */
  STOPPING_BUS_SERVICE(703, "STOPPING_BUS_SERVICE"),
  /** Local Bus Service. */
  LOCAL_BUS_SERVICE(704, "LOCAL_BUS_SERVICE"),
  /** Night Bus Service. */
  NIGHT_BUS_SERVICE(705, "NIGHT_BUS_SERVICE"),
  /** Post Bus Service. */
  POST_BUS_SERVICE(706, "POST_BUS_SERVICE"),
  /** Special Needs Bus. */
  SPECIAL_NEEDS_BUS(707, "SPECIAL_NEEDS_BUS"),
  /** Mobility Bus Service. */
  MOBILITY_BUS_SERVICE(708, "MOBILITY_BUS_SERVICE"),
  /** Mobility Bus for Registered Disabled. */
  MOBILITY_BUS_FOR_REGISTERED_DISABLED(709, "MOBILITY_BUS_FOR_REGISTERED_DISABLED"),
  /** Sightseeing Bus. */
  SIGHTSEEING_BUS(710, "SIGHTSEEING_BUS"),
  /** Shuttle Bus. */
  SHUTTLE_BUS(711, "SHUTTLE_BUS"),
  /** School Bus. */
  SCHOOL_BUS(712, "SCHOOL_BUS"),
  /** School and Public Service Bus. */
  SCHOOL_AND_PUBLIC_SERVICE_BUS(713, "SCHOOL_AND_PUBLIC_SERVICE_BUS"),
  /** Rail Replacement Bus Service. */
  RAIL_REPLACEMENT_BUS_SERVICE(714, "RAIL_REPLACEMENT_BUS_SERVICE"),
  /** Demand and Response Bus Service. */
  DEMAND_AND_RESPONSE_BUS_SERVICE(715, "DEMAND_AND_RESPONSE_BUS_SERVICE"),
  /** All Bus Services. */
  ALL_BUS_SERVICES(716, "ALL_BUS_SERVICES"),

  /** Trolleybus Service. */
  TROLLEYBUS_SERVICE(800, "TROLLEYBUS_SERVICE"),

  /** Tram Service. */
  TRAM_SERVICE(900, "TRAM_SERVICE"),
  /** City Tram Service. */
  CITY_TRAM_SERVICE(901, "CITY_TRAM_SERVICE"),
  /** Local Tram Service. */
  LOCAL_TRAM_SERVICE(902, "LOCAL_TRAM_SERVICE"),
  /** Regional Tram Service. */
  REGIONAL_TRAM_SERVICE(903, "REGIONAL_TRAM_SERVICE"),
  /** Sightseeing Tram Service. */
  SIGHTSEEING_TRAM_SERVICE(904, "SIGHTSEEING_TRAM_SERVICE"),
  /** Shuttle Tram Service. */
  SHUTTLE_TRAM_SERVICE(905, "SHUTTLE_TRAM_SERVICE"),
  /** All Tram Services. */
  ALL_TRAM_SERVICES(906, "ALL_TRAM_SERVICES"),

  /** Water Transport Service. */
  WATER_TRANSPORT_SERVICE(1000, "WATER_TRANSPORT_SERVICE"),

  /** Air Service. */
  AIR_SERVICE(1100, "AIR_SERVICE"),

  /** Ferry Service. */
  FERRY_SERVICE(1200, "FERRY_SERVICE"),

  /** Aerial Lift Service. */
  AERIAL_LIFT_SERVICE(1300, "AERIAL_LIFT_SERVICE"),
  /** Telecabin Service. */
  TELECABIN_SERVICE(1301, "TELECABIN_SERVICE"),
  /** Cable Car Service. */
  CABLE_CAR_SERVICE(1302, "CABLE_CAR_SERVICE"),
  /** Elevator Service. */
  ELEVATOR_SERVICE(1303, "ELEVATOR_SERVICE"),
  /** Chair Lift Service. */
  CHAIR_LIFT_SERVICE(1304, "CHAIR_LIFT_SERVICE"),
  /** Drag Lift Service. */
  DRAG_LIFT_SERVICE(1305, "DRAG_LIFT_SERVICE"),
  /** Small Telecabin Service. */
  SMALL_TELECABIN_SERVICE(1306, "SMALL_TELECABIN_SERVICE"),
  /** All Telecabin Services. */
  ALL_TELECABIN_SERVICES(1307, "ALL_TELECABIN_SERVICES"),

  /** Funicular Service. */
  FUNICULAR_SERVICE(1400, "FUNICULAR_SERVICE"),

  /** Taxi Service. */
  TAXI_SERVICE(1500, "TAXI_SERVICE"),
  /** Communal Taxi Service. */
  COMMUNAL_TAXI_SERVICE(1501, "COMMUNAL_TAXI_SERVICE"),
  /** Water Taxi Service. */
  WATER_TAXI_SERVICE(1502, "WATER_TAXI_SERVICE"),
  /** Rail Taxi Service. */
  RAIL_TAXI_SERVICE(1503, "RAIL_TAXI_SERVICE"),
  /** Bike Taxi Service. */
  BIKE_TAXI_SERVICE(1504, "BIKE_TAXI_SERVICE"),
  /** Licensed Taxi Service. */
  LICENSED_TAXI_SERVICE(1505, "LICENSED_TAXI_SERVICE"),
  /** Private Hire Service Vehicle. */
  PRIVATE_HIRE_SERVICE_VEHICLE(1506, "PRIVATE_HIRE_SERVICE_VEHICLE"),
  /** All Taxi Services. */
  ALL_TAXI_SERVICES(1507, "ALL_TAXI_SERVICES"),

  /** Miscellaneous Service. */
  MISCELLANEOUS_SERVICE(1700, "MISCELLANEOUS_SERVICE"),
  /** Horse-drawn Carriage. */
  HORSE_DRAWN_CARRIAGE(1702, "HORSE_DRAWN_CARRIAGE");

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
        ?: throw IllegalArgumentException(
          "Unknown RouteType value: $value. Valid values: ${entries.joinToString { it.value }}"
        )
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
        ?: throw IllegalArgumentException(
          "Unknown GTFS route type: $gtfsValue. Valid values: ${entries.joinToString { "${it.value}(${it.gtfsValue})" }}"
        )
    }
  }
}
