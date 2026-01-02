/**
 * GTFS route type enumeration aligned with General Transit Feed Specification.
 *
 * Route types are hierarchical indicators of service mode and passenger comfort.
 * They influence UI presentation, scheduling analysis, and accessibility considerations.
 *
 * Reference: https://gtfs.org/documentation/schedule/reference/#routestxt
 * Backend: com.mobilispect.backend.route.domain.model.RouteType
 */
export enum RouteType {
  TRAM = 'TRAM',
  SUBWAY = 'SUBWAY',
  RAIL = 'RAIL',
  BUS = 'BUS',
  FERRY = 'FERRY',
  CABLE_TRAM = 'CABLE_TRAM',
  AERIAL_LIFT = 'AERIAL_LIFT',
  FUNICULAR = 'FUNICULAR',
  TROLLEYBUS = 'TROLLEYBUS',
  MONORAIL = 'MONORAIL',
  RAILWAY_SERVICE = 'RAILWAY_SERVICE',
  HIGH_SPEED_RAIL_SERVICE = 'HIGH_SPEED_RAIL_SERVICE',
  LONG_DISTANCE_TRAINS = 'LONG_DISTANCE_TRAINS',
  INTER_REGIONAL_RAIL_SERVICE = 'INTER_REGIONAL_RAIL_SERVICE',
  CAR_TRANSPORT_RAIL_SERVICE = 'CAR_TRANSPORT_RAIL_SERVICE',
  SLEEPER_RAIL_SERVICE = 'SLEEPER_RAIL_SERVICE',
  REGIONAL_RAIL_SERVICE = 'REGIONAL_RAIL_SERVICE',
  TOURIST_RAILWAY_SERVICE = 'TOURIST_RAILWAY_SERVICE',
  RAIL_SHUTTLE = 'RAIL_SHUTTLE',
  SUBURBAN_RAILWAY = 'SUBURBAN_RAILWAY',
  REPLACEMENT_RAIL_SERVICE = 'REPLACEMENT_RAIL_SERVICE',
  SPECIAL_RAIL_SERVICE = 'SPECIAL_RAIL_SERVICE',
  LORRY_TRANSPORT_RAIL_SERVICE = 'LORRY_TRANSPORT_RAIL_SERVICE',
  ALL_RAIL_SERVICES = 'ALL_RAIL_SERVICES',
  CROSS_COUNTRY_RAIL_SERVICE = 'CROSS_COUNTRY_RAIL_SERVICE',
  VEHICLE_TRANSPORT_RAIL_SERVICE = 'VEHICLE_TRANSPORT_RAIL_SERVICE',
  RACK_AND_PINION_RAILWAY = 'RACK_AND_PINION_RAILWAY',
  ADDITIONAL_RAIL_SERVICE = 'ADDITIONAL_RAIL_SERVICE',
  COACH_SERVICE = 'COACH_SERVICE',
  INTERNATIONAL_COACH_SERVICE = 'INTERNATIONAL_COACH_SERVICE',
  NATIONAL_COACH_SERVICE = 'NATIONAL_COACH_SERVICE',
  SHUTTLE_COACH_SERVICE = 'SHUTTLE_COACH_SERVICE',
  REGIONAL_COACH_SERVICE = 'REGIONAL_COACH_SERVICE',
  SPECIAL_COACH_SERVICE = 'SPECIAL_COACH_SERVICE',
  SIGHTSEEING_COACH_SERVICE = 'SIGHTSEEING_COACH_SERVICE',
  TOURIST_COACH_SERVICE = 'TOURIST_COACH_SERVICE',
  COMMUTER_COACH_SERVICE = 'COMMUTER_COACH_SERVICE',
  ALL_COACH_SERVICES = 'ALL_COACH_SERVICES',
  URBAN_RAILWAY_SERVICE = 'URBAN_RAILWAY_SERVICE',
  METRO_SERVICE = 'METRO_SERVICE',
  UNDERGROUND_SERVICE = 'UNDERGROUND_SERVICE',
  URBAN_RAILWAY_SERVICE_403 = 'URBAN_RAILWAY_SERVICE_403',
  ALL_URBAN_RAILWAY_SERVICES = 'ALL_URBAN_RAILWAY_SERVICES',
  MONORAIL_405 = 'MONORAIL_405',
  BUS_SERVICE = 'BUS_SERVICE',
  REGIONAL_BUS_SERVICE = 'REGIONAL_BUS_SERVICE',
  EXPRESS_BUS_SERVICE = 'EXPRESS_BUS_SERVICE',
  STOPPING_BUS_SERVICE = 'STOPPING_BUS_SERVICE',
  LOCAL_BUS_SERVICE = 'LOCAL_BUS_SERVICE',
  NIGHT_BUS_SERVICE = 'NIGHT_BUS_SERVICE',
  POST_BUS_SERVICE = 'POST_BUS_SERVICE',
  SPECIAL_NEEDS_BUS = 'SPECIAL_NEEDS_BUS',
  MOBILITY_BUS_SERVICE = 'MOBILITY_BUS_SERVICE',
  MOBILITY_BUS_FOR_REGISTERED_DISABLED = 'MOBILITY_BUS_FOR_REGISTERED_DISABLED',
  SIGHTSEEING_BUS = 'SIGHTSEEING_BUS',
  SHUTTLE_BUS = 'SHUTTLE_BUS',
  SCHOOL_BUS = 'SCHOOL_BUS',
  SCHOOL_AND_PUBLIC_SERVICE_BUS = 'SCHOOL_AND_PUBLIC_SERVICE_BUS',
  RAIL_REPLACEMENT_BUS_SERVICE = 'RAIL_REPLACEMENT_BUS_SERVICE',
  DEMAND_AND_RESPONSE_BUS_SERVICE = 'DEMAND_AND_RESPONSE_BUS_SERVICE',
  ALL_BUS_SERVICES = 'ALL_BUS_SERVICES',
  TROLLEYBUS_SERVICE = 'TROLLEYBUS_SERVICE',
  TRAM_SERVICE = 'TRAM_SERVICE',
  CITY_TRAM_SERVICE = 'CITY_TRAM_SERVICE',
  LOCAL_TRAM_SERVICE = 'LOCAL_TRAM_SERVICE',
  REGIONAL_TRAM_SERVICE = 'REGIONAL_TRAM_SERVICE',
  SIGHTSEEING_TRAM_SERVICE = 'SIGHTSEEING_TRAM_SERVICE',
  SHUTTLE_TRAM_SERVICE = 'SHUTTLE_TRAM_SERVICE',
  ALL_TRAM_SERVICES = 'ALL_TRAM_SERVICES',
  WATER_TRANSPORT_SERVICE = 'WATER_TRANSPORT_SERVICE',
  AIR_SERVICE = 'AIR_SERVICE',
  FERRY_SERVICE = 'FERRY_SERVICE',
  AERIAL_LIFT_SERVICE = 'AERIAL_LIFT_SERVICE',
  TELECABIN_SERVICE = 'TELECABIN_SERVICE',
  CABLE_CAR_SERVICE = 'CABLE_CAR_SERVICE',
  ELEVATOR_SERVICE = 'ELEVATOR_SERVICE',
  CHAIR_LIFT_SERVICE = 'CHAIR_LIFT_SERVICE',
  DRAG_LIFT_SERVICE = 'DRAG_LIFT_SERVICE',
  SMALL_TELECABIN_SERVICE = 'SMALL_TELECABIN_SERVICE',
  ALL_TELECABIN_SERVICES = 'ALL_TELECABIN_SERVICES',
  FUNICULAR_SERVICE = 'FUNICULAR_SERVICE',
  TAXI_SERVICE = 'TAXI_SERVICE',
  COMMUNAL_TAXI_SERVICE = 'COMMUNAL_TAXI_SERVICE',
  WATER_TAXI_SERVICE = 'WATER_TAXI_SERVICE',
  RAIL_TAXI_SERVICE = 'RAIL_TAXI_SERVICE',
  BIKE_TAXI_SERVICE = 'BIKE_TAXI_SERVICE',
  LICENSED_TAXI_SERVICE = 'LICENSED_TAXI_SERVICE',
  PRIVATE_HIRE_SERVICE_VEHICLE = 'PRIVATE_HIRE_SERVICE_VEHICLE',
  ALL_TAXI_SERVICES = 'ALL_TAXI_SERVICES',
  MISCELLANEOUS_SERVICE = 'MISCELLANEOUS_SERVICE',
  HORSE_DRAWN_CARRIAGE = 'HORSE_DRAWN_CARRIAGE',
}

/**
 * Mapping of RouteType enum values to their GTFS numeric codes.
 */
export const RouteTypeGtfsValues: Record<RouteType, number> = {
  [RouteType.TRAM]: 0,
  [RouteType.SUBWAY]: 1,
  [RouteType.RAIL]: 2,
  [RouteType.BUS]: 3,
  [RouteType.FERRY]: 4,
  [RouteType.CABLE_TRAM]: 5,
  [RouteType.AERIAL_LIFT]: 6,
  [RouteType.FUNICULAR]: 7,
  [RouteType.TROLLEYBUS]: 11,
  [RouteType.MONORAIL]: 12,
  [RouteType.RAILWAY_SERVICE]: 100,
  [RouteType.HIGH_SPEED_RAIL_SERVICE]: 101,
  [RouteType.LONG_DISTANCE_TRAINS]: 102,
  [RouteType.INTER_REGIONAL_RAIL_SERVICE]: 103,
  [RouteType.CAR_TRANSPORT_RAIL_SERVICE]: 104,
  [RouteType.SLEEPER_RAIL_SERVICE]: 105,
  [RouteType.REGIONAL_RAIL_SERVICE]: 106,
  [RouteType.TOURIST_RAILWAY_SERVICE]: 107,
  [RouteType.RAIL_SHUTTLE]: 108,
  [RouteType.SUBURBAN_RAILWAY]: 109,
  [RouteType.REPLACEMENT_RAIL_SERVICE]: 110,
  [RouteType.SPECIAL_RAIL_SERVICE]: 111,
  [RouteType.LORRY_TRANSPORT_RAIL_SERVICE]: 112,
  [RouteType.ALL_RAIL_SERVICES]: 113,
  [RouteType.CROSS_COUNTRY_RAIL_SERVICE]: 114,
  [RouteType.VEHICLE_TRANSPORT_RAIL_SERVICE]: 115,
  [RouteType.RACK_AND_PINION_RAILWAY]: 116,
  [RouteType.ADDITIONAL_RAIL_SERVICE]: 117,
  [RouteType.COACH_SERVICE]: 200,
  [RouteType.INTERNATIONAL_COACH_SERVICE]: 201,
  [RouteType.NATIONAL_COACH_SERVICE]: 202,
  [RouteType.SHUTTLE_COACH_SERVICE]: 203,
  [RouteType.REGIONAL_COACH_SERVICE]: 204,
  [RouteType.SPECIAL_COACH_SERVICE]: 205,
  [RouteType.SIGHTSEEING_COACH_SERVICE]: 206,
  [RouteType.TOURIST_COACH_SERVICE]: 207,
  [RouteType.COMMUTER_COACH_SERVICE]: 208,
  [RouteType.ALL_COACH_SERVICES]: 209,
  [RouteType.URBAN_RAILWAY_SERVICE]: 400,
  [RouteType.METRO_SERVICE]: 401,
  [RouteType.UNDERGROUND_SERVICE]: 402,
  [RouteType.URBAN_RAILWAY_SERVICE_403]: 403,
  [RouteType.ALL_URBAN_RAILWAY_SERVICES]: 404,
  [RouteType.MONORAIL_405]: 405,
  [RouteType.BUS_SERVICE]: 700,
  [RouteType.REGIONAL_BUS_SERVICE]: 701,
  [RouteType.EXPRESS_BUS_SERVICE]: 702,
  [RouteType.STOPPING_BUS_SERVICE]: 703,
  [RouteType.LOCAL_BUS_SERVICE]: 704,
  [RouteType.NIGHT_BUS_SERVICE]: 705,
  [RouteType.POST_BUS_SERVICE]: 706,
  [RouteType.SPECIAL_NEEDS_BUS]: 707,
  [RouteType.MOBILITY_BUS_SERVICE]: 708,
  [RouteType.MOBILITY_BUS_FOR_REGISTERED_DISABLED]: 709,
  [RouteType.SIGHTSEEING_BUS]: 710,
  [RouteType.SHUTTLE_BUS]: 711,
  [RouteType.SCHOOL_BUS]: 712,
  [RouteType.SCHOOL_AND_PUBLIC_SERVICE_BUS]: 713,
  [RouteType.RAIL_REPLACEMENT_BUS_SERVICE]: 714,
  [RouteType.DEMAND_AND_RESPONSE_BUS_SERVICE]: 715,
  [RouteType.ALL_BUS_SERVICES]: 716,
  [RouteType.TROLLEYBUS_SERVICE]: 800,
  [RouteType.TRAM_SERVICE]: 900,
  [RouteType.CITY_TRAM_SERVICE]: 901,
  [RouteType.LOCAL_TRAM_SERVICE]: 902,
  [RouteType.REGIONAL_TRAM_SERVICE]: 903,
  [RouteType.SIGHTSEEING_TRAM_SERVICE]: 904,
  [RouteType.SHUTTLE_TRAM_SERVICE]: 905,
  [RouteType.ALL_TRAM_SERVICES]: 906,
  [RouteType.WATER_TRANSPORT_SERVICE]: 1000,
  [RouteType.AIR_SERVICE]: 1100,
  [RouteType.FERRY_SERVICE]: 1200,
  [RouteType.AERIAL_LIFT_SERVICE]: 1300,
  [RouteType.TELECABIN_SERVICE]: 1301,
  [RouteType.CABLE_CAR_SERVICE]: 1302,
  [RouteType.ELEVATOR_SERVICE]: 1303,
  [RouteType.CHAIR_LIFT_SERVICE]: 1304,
  [RouteType.DRAG_LIFT_SERVICE]: 1305,
  [RouteType.SMALL_TELECABIN_SERVICE]: 1306,
  [RouteType.ALL_TELECABIN_SERVICES]: 1307,
  [RouteType.FUNICULAR_SERVICE]: 1400,
  [RouteType.TAXI_SERVICE]: 1500,
  [RouteType.COMMUNAL_TAXI_SERVICE]: 1501,
  [RouteType.WATER_TAXI_SERVICE]: 1502,
  [RouteType.RAIL_TAXI_SERVICE]: 1503,
  [RouteType.BIKE_TAXI_SERVICE]: 1504,
  [RouteType.LICENSED_TAXI_SERVICE]: 1505,
  [RouteType.PRIVATE_HIRE_SERVICE_VEHICLE]: 1506,
  [RouteType.ALL_TAXI_SERVICES]: 1507,
  [RouteType.MISCELLANEOUS_SERVICE]: 1700,
  [RouteType.HORSE_DRAWN_CARRIAGE]: 1702,
};

/**
 * Inverse mapping from GTFS numeric codes to RouteType enum values.
 */
export const GtfsValuesToRouteType: Record<number, RouteType> = {
  0: RouteType.TRAM,
  1: RouteType.SUBWAY,
  2: RouteType.RAIL,
  3: RouteType.BUS,
  4: RouteType.FERRY,
  5: RouteType.CABLE_TRAM,
  6: RouteType.AERIAL_LIFT,
  7: RouteType.FUNICULAR,
  11: RouteType.TROLLEYBUS,
  12: RouteType.MONORAIL,
  100: RouteType.RAILWAY_SERVICE,
  101: RouteType.HIGH_SPEED_RAIL_SERVICE,
  102: RouteType.LONG_DISTANCE_TRAINS,
  103: RouteType.INTER_REGIONAL_RAIL_SERVICE,
  104: RouteType.CAR_TRANSPORT_RAIL_SERVICE,
  105: RouteType.SLEEPER_RAIL_SERVICE,
  106: RouteType.REGIONAL_RAIL_SERVICE,
  107: RouteType.TOURIST_RAILWAY_SERVICE,
  108: RouteType.RAIL_SHUTTLE,
  109: RouteType.SUBURBAN_RAILWAY,
  110: RouteType.REPLACEMENT_RAIL_SERVICE,
  111: RouteType.SPECIAL_RAIL_SERVICE,
  112: RouteType.LORRY_TRANSPORT_RAIL_SERVICE,
  113: RouteType.ALL_RAIL_SERVICES,
  114: RouteType.CROSS_COUNTRY_RAIL_SERVICE,
  115: RouteType.VEHICLE_TRANSPORT_RAIL_SERVICE,
  116: RouteType.RACK_AND_PINION_RAILWAY,
  117: RouteType.ADDITIONAL_RAIL_SERVICE,
  200: RouteType.COACH_SERVICE,
  201: RouteType.INTERNATIONAL_COACH_SERVICE,
  202: RouteType.NATIONAL_COACH_SERVICE,
  203: RouteType.SHUTTLE_COACH_SERVICE,
  204: RouteType.REGIONAL_COACH_SERVICE,
  205: RouteType.SPECIAL_COACH_SERVICE,
  206: RouteType.SIGHTSEEING_COACH_SERVICE,
  207: RouteType.TOURIST_COACH_SERVICE,
  208: RouteType.COMMUTER_COACH_SERVICE,
  209: RouteType.ALL_COACH_SERVICES,
  400: RouteType.URBAN_RAILWAY_SERVICE,
  401: RouteType.METRO_SERVICE,
  402: RouteType.UNDERGROUND_SERVICE,
  403: RouteType.URBAN_RAILWAY_SERVICE_403,
  404: RouteType.ALL_URBAN_RAILWAY_SERVICES,
  405: RouteType.MONORAIL_405,
  700: RouteType.BUS_SERVICE,
  701: RouteType.REGIONAL_BUS_SERVICE,
  702: RouteType.EXPRESS_BUS_SERVICE,
  703: RouteType.STOPPING_BUS_SERVICE,
  704: RouteType.LOCAL_BUS_SERVICE,
  705: RouteType.NIGHT_BUS_SERVICE,
  706: RouteType.POST_BUS_SERVICE,
  707: RouteType.SPECIAL_NEEDS_BUS,
  708: RouteType.MOBILITY_BUS_SERVICE,
  709: RouteType.MOBILITY_BUS_FOR_REGISTERED_DISABLED,
  710: RouteType.SIGHTSEEING_BUS,
  711: RouteType.SHUTTLE_BUS,
  712: RouteType.SCHOOL_BUS,
  713: RouteType.SCHOOL_AND_PUBLIC_SERVICE_BUS,
  714: RouteType.RAIL_REPLACEMENT_BUS_SERVICE,
  715: RouteType.DEMAND_AND_RESPONSE_BUS_SERVICE,
  716: RouteType.ALL_BUS_SERVICES,
  800: RouteType.TROLLEYBUS_SERVICE,
  900: RouteType.TRAM_SERVICE,
  901: RouteType.CITY_TRAM_SERVICE,
  902: RouteType.LOCAL_TRAM_SERVICE,
  903: RouteType.REGIONAL_TRAM_SERVICE,
  904: RouteType.SIGHTSEEING_TRAM_SERVICE,
  905: RouteType.SHUTTLE_TRAM_SERVICE,
  906: RouteType.ALL_TRAM_SERVICES,
  1000: RouteType.WATER_TRANSPORT_SERVICE,
  1100: RouteType.AIR_SERVICE,
  1200: RouteType.FERRY_SERVICE,
  1300: RouteType.AERIAL_LIFT_SERVICE,
  1301: RouteType.TELECABIN_SERVICE,
  1302: RouteType.CABLE_CAR_SERVICE,
  1303: RouteType.ELEVATOR_SERVICE,
  1304: RouteType.CHAIR_LIFT_SERVICE,
  1305: RouteType.DRAG_LIFT_SERVICE,
  1306: RouteType.SMALL_TELECABIN_SERVICE,
  1307: RouteType.ALL_TELECABIN_SERVICES,
  1400: RouteType.FUNICULAR_SERVICE,
  1500: RouteType.TAXI_SERVICE,
  1501: RouteType.COMMUNAL_TAXI_SERVICE,
  1502: RouteType.WATER_TAXI_SERVICE,
  1503: RouteType.RAIL_TAXI_SERVICE,
  1504: RouteType.BIKE_TAXI_SERVICE,
  1505: RouteType.LICENSED_TAXI_SERVICE,
  1506: RouteType.PRIVATE_HIRE_SERVICE_VEHICLE,
  1507: RouteType.ALL_TAXI_SERVICES,
  1700: RouteType.MISCELLANEOUS_SERVICE,
  1702: RouteType.HORSE_DRAWN_CARRIAGE,
};

const routeTypeValues = new Set<string>(Object.values(RouteType));

/**
 * Maps route type enum values to human-readable labels.
 */
export const RouteTypeLabels: Partial<Record<RouteType, string>> = {
  [RouteType.TRAM]: 'Tram',
  [RouteType.SUBWAY]: 'Subway',
  [RouteType.RAIL]: 'Rail',
  [RouteType.BUS]: 'Bus',
  [RouteType.FERRY]: 'Ferry',
  [RouteType.CABLE_TRAM]: 'Cable Tram',
  [RouteType.AERIAL_LIFT]: 'Aerial Lift',
  [RouteType.FUNICULAR]: 'Funicular',
  [RouteType.TROLLEYBUS]: 'Trolleybus',
  [RouteType.MONORAIL]: 'Monorail',
};

/**
 * Maps route type enum values to CSS icon classes for UI representation.
 */
export const RouteTypeIcons: Partial<Record<RouteType, string>> = {
  [RouteType.TRAM]: 'tram',
  [RouteType.SUBWAY]: 'subway',
  [RouteType.RAIL]: 'train',
  [RouteType.BUS]: 'bus',
  [RouteType.FERRY]: 'directions_boat',
  [RouteType.CABLE_TRAM]: 'cable_car',
  [RouteType.AERIAL_LIFT]: 'cable_car',
  [RouteType.FUNICULAR]: 'trending_up',
  [RouteType.TROLLEYBUS]: 'electric_bus',
  [RouteType.MONORAIL]: 'train',
};

const titleCase = (value: string): string =>
  value
    .toLowerCase()
    .split(' ')
    .map((word) => word.charAt(0).toUpperCase() + word.slice(1))
    .join(' ');

const formatRouteTypeLabel = (value: string): string => {
  const suffixMatch = value.match(/^(.*)_(\d{3,4})$/);
  if (suffixMatch) {
    const baseLabel = titleCase(suffixMatch[1].replace(/_/g, ' '));
    return `${baseLabel} (${suffixMatch[2]})`;
  }

  return titleCase(value.replace(/_/g, ' '));
};

/**
 * Gets the human-readable label for a route type.
 * @param routeType The route type enum value
 * @returns The display label for the route type
 */
export function getRouteTypeLabel(routeType: RouteType): string {
  if (!routeTypeValues.has(routeType)) {
    return routeType;
  }

  return RouteTypeLabels[routeType] ?? formatRouteTypeLabel(routeType);
}

/**
 * Gets the Material icon name for a route type.
 * @param routeType The route type enum value
 * @returns The Material icon name for the route type
 */
export function getRouteTypeIcon(routeType: RouteType): string {
  return RouteTypeIcons[routeType] || 'directions_transit';
}

/**
 * Gets the GTFS numeric code for a route type.
 * @param routeType The route type enum value
 * @returns The GTFS code for the route type
 */
export function getRouteTypeGtfsValue(routeType: RouteType): number {
  return RouteTypeGtfsValues[routeType];
}

/**
 * Converts a GTFS numeric code to a RouteType enum value.
 * @param gtfsValue The GTFS route type code
 * @returns The corresponding RouteType enum value
 * @throws Error if gtfsValue is not a valid GTFS route code
 */
export function getRouteTypeFromGtfsValue(gtfsValue: number): RouteType {
  const routeType = GtfsValuesToRouteType[gtfsValue];
  if (!routeType) {
    throw new Error(
      `Unknown GTFS route type: ${gtfsValue}. Valid values: ${Object.keys(GtfsValuesToRouteType).join(', ')}`,
    );
  }
  return routeType;
}

/**
 * Retrieves all available route types.
 * @returns Array of all RouteType enum values
 */
export function getAllRouteTypes(): RouteType[] {
  return Object.values(RouteType);
}
