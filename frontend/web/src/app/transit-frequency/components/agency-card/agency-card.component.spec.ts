import { AgencyCardComponent } from './agency-card.component';

describe('AgencyCardComponent', () => {
  it('detects route presence and formats types', () => {
    const component = new AgencyCardComponent();
    component.agency = {
      id: 'agency-1',
      name: 'Metro',
      feedOnestopId: 'f-metro',
      regionIds: ['r-1'],
      routeCount: 2,
      activeRouteCount: 2,
      routesByType: { bus: 2, tram: 0 }
    };

    expect(component.hasRoutes).toBeTrue();
    expect(component.routeTypes).toEqual(['bus', 'tram']);
    expect(component.formatRouteType('BUS')).toBe('Bus');
  });

  it('handles missing route data', () => {
    const component = new AgencyCardComponent();
    component.agency = {
      id: 'agency-2',
      name: 'Empty',
      feedOnestopId: 'f-empty',
      regionIds: [],
      routeCount: 0,
      activeRouteCount: 0,
      routesByType: {}
    };

    expect(component.hasRoutes).toBeFalse();
    expect(component.routeTypes).toEqual([]);
  });
});
