/**
 * GTFS route type enumeration aligned with General Transit Feed Specification.
 *
 * Route types are hierarchical indicators of service mode and passenger comfort.
 * They influence UI presentation, scheduling analysis, and accessibility considerations.
 *
 * Reference: https://gtfs.org/documentation/schedule/reference/#routestxt
 * Backend: com.mobilispect.backend.transitanalysis.domain.model.RouteType
 */
export enum RouteType {
  /**
   * Tram, Streetcar, Light rail.
   * Lightweight rail transit typically operated at street level.
   * GTFS code: 0
   */
  TRAM = 'TRAM',

  /**
   * Subway, Metro.
   * Heavy rail transit typically underground with high capacity.
   * GTFS code: 1
   */
  SUBWAY = 'SUBWAY',

  /**
   * Rail, Intercity rail.
   * Regional or intercity rail service.
   * GTFS code: 2
   */
  RAIL = 'RAIL',

  /**
   * Bus.
   * Standard bus service (most common transit mode).
   * GTFS code: 3
   */
  BUS = 'BUS',

  /**
   * Ferry.
   * Water-based passenger service.
   * GTFS code: 4
   */
  FERRY = 'FERRY',

  /**
   * Cable tram.
   * Cable-driven streetcar system.
   * GTFS code: 5
   */
  CABLE_TRAM = 'CABLE_TRAM',

  /**
   * Aerial lift, Suspended cable car.
   * Cable-driven transportation system suspended above ground.
   * GTFS code: 6
   */
  AERIAL_LIFT = 'AERIAL_LIFT',

  /**
   * Funicular.
   * Rail-based system on steep inclines.
   * GTFS code: 7
   */
  FUNICULAR = 'FUNICULAR',

  /**
   * Trolleybus, Trackless trolley.
   * Electric bus powered by overhead lines.
   * GTFS code: 11
   */
  TROLLEYBUS = 'TROLLEYBUS',

  /**
   * Monorail.
   * Single-rail elevated system.
   * GTFS code: 12
   */
  MONORAIL = 'MONORAIL',
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
};

/**
 * Maps route type enum values to human-readable labels.
 */
export const RouteTypeLabels: Record<RouteType, string> = {
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
export const RouteTypeIcons: Record<RouteType, string> = {
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

/**
 * Gets the human-readable label for a route type.
 * @param routeType The route type enum value
 * @returns The display label for the route type
 */
export function getRouteTypeLabel(routeType: RouteType): string {
  return RouteTypeLabels[routeType] || routeType;
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
