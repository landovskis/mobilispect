import { RouteBreadcrumbResolver } from './route-breadcrumb.resolver';
import { RouteService, RouteDto } from '../services/route.service';
import { firstValueFrom, of, throwError } from 'rxjs';
import { ActivatedRouteSnapshot } from '@angular/router';
import { TestBed } from '@angular/core/testing';
import { vi } from 'vitest';

describe('RouteBreadcrumbResolver', () => {
  let resolver: RouteBreadcrumbResolver;
  let routeServiceSpy: RouteService;

  const mockRoute: RouteDto = {
    id: 'r-123',
    agencyId: 'a-123',
    shortName: '15',
    longName: 'Sainte-Catherine',
    routeType: 'BUS',
    active: true,
  };

  beforeEach(() => {
    routeServiceSpy = {
      getRoute: vi.fn(),
    } as unknown as RouteService;
    TestBed.configureTestingModule({
      providers: [{ provide: RouteService, useValue: routeServiceSpy }, RouteBreadcrumbResolver],
    });
    resolver = TestBed.inject(RouteBreadcrumbResolver);
  });

  const createSnapshot = (routeId: string | null): ActivatedRouteSnapshot => {
    return {
      paramMap: {
        get: (key: string) => (key === 'routeId' ? routeId : null),
      },
    } as unknown as ActivatedRouteSnapshot;
  };

  it('returns generic label when route id is missing', async () => {
    const snapshot = createSnapshot(null);

    const label = await firstValueFrom(resolver.resolve(snapshot));

    expect(label).toBe('Route');
    expect(routeServiceSpy.getRoute).not.toHaveBeenCalled();
  });

  it('formats label with short and long name when both exist', async () => {
    vi.mocked(routeServiceSpy.getRoute).mockReturnValue(of(mockRoute));
    const snapshot = createSnapshot(mockRoute.id);

    const label = await firstValueFrom(resolver.resolve(snapshot));

    expect(label).toBe('15 Sainte-Catherine');
    expect(routeServiceSpy.getRoute).toHaveBeenCalledWith(mockRoute.id);
  });

  it('uses short name when long name is missing', async () => {
    const route = { ...mockRoute, longName: '' };
    vi.mocked(routeServiceSpy.getRoute).mockReturnValue(of(route));
    const snapshot = createSnapshot(mockRoute.id);

    const label = await firstValueFrom(resolver.resolve(snapshot));

    expect(label).toBe('15');
  });

  it('uses long name when short name is missing', async () => {
    const route = { ...mockRoute, shortName: null };
    vi.mocked(routeServiceSpy.getRoute).mockReturnValue(of(route));
    const snapshot = createSnapshot(mockRoute.id);

    const label = await firstValueFrom(resolver.resolve(snapshot));

    expect(label).toBe('Sainte-Catherine');
  });

  it('falls back to route id on api error', async () => {
    vi.mocked(routeServiceSpy.getRoute).mockReturnValue(throwError(() => new Error('boom')));
    const snapshot = createSnapshot(mockRoute.id);

    const label = await firstValueFrom(resolver.resolve(snapshot));

    expect(label).toBe(mockRoute.id);
  });
});
