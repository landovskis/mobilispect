import {
  getAllRouteTypes,
  getRouteTypeFromGtfsValue,
  getRouteTypeGtfsValue,
  getRouteTypeIcon,
  getRouteTypeLabel,
  RouteType,
} from './route-type.model';

describe('Route type helpers', () => {
  it('maps labels and icons', () => {
    expect(getRouteTypeLabel(RouteType.BUS)).toBe('Bus');
    expect(getRouteTypeIcon(RouteType.TRAM)).toBe('tram');
    expect(getRouteTypeLabel('UNKNOWN' as RouteType)).toBe('UNKNOWN');
  });

  it('maps GTFS values and validates invalid codes', () => {
    expect(getRouteTypeGtfsValue(RouteType.SUBWAY)).toBe(1);
    expect(getRouteTypeFromGtfsValue(3)).toBe(RouteType.BUS);
    expect(() => getRouteTypeFromGtfsValue(999)).toThrowError(/Unknown GTFS route type/);
  });

  it('returns all route types', () => {
    const types = getAllRouteTypes();
    expect(types).toContain(RouteType.RAIL);
    expect(types).toContain(RouteType.MONORAIL);
  });
});
