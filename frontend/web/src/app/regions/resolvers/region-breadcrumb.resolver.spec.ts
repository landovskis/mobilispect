import { RegionBreadcrumbResolver } from './region-breadcrumb.resolver';
import { RegionService } from '../../feeds/services/region.service';
import {
  MetropolitanRegion,
  MetropolitanRegionDetail,
} from '../../feeds/models/region.models';
import { firstValueFrom, of, throwError } from 'rxjs';
import { ActivatedRouteSnapshot } from '@angular/router';
import { TestBed } from '@angular/core/testing';

describe('RegionBreadcrumbResolver', () => {
  let resolver: RegionBreadcrumbResolver;
  let regionServiceSpy: jasmine.SpyObj<RegionService>;

  const mockRegion: MetropolitanRegion = {
    regionOnestopId: 'r-montr-al-qu-bec-canada',
    name: 'Montréal',
    adm0Name: 'Canada',
    adm1Name: 'Québec',
    autoUpdateEnabled: true,
    feedCount: 10,
    lastCheckAt: null,
    createdAt: '2024-01-01T00:00:00Z',
    updatedAt: '2024-01-01T00:00:00Z',
  };

  const mockRegionDetail: MetropolitanRegionDetail = {
    ...mockRegion,
    feeds: [],
  };

  beforeEach(() => {
    regionServiceSpy = jasmine.createSpyObj('RegionService', [
      'getRegion',
      'getCachedRegion',
    ]);
    TestBed.configureTestingModule({
      providers: [
        { provide: RegionService, useValue: regionServiceSpy },
        RegionBreadcrumbResolver,
      ],
    });
    resolver = TestBed.inject(RegionBreadcrumbResolver);
  });

  const createSnapshot = (regionId: string | null): ActivatedRouteSnapshot => {
    return {
      paramMap: {
        get: (key: string) => (key === 'regionId' ? regionId : null),
      },
    } as unknown as ActivatedRouteSnapshot;
  };

  it('returns generic label when region id is missing', async () => {
    const snapshot = createSnapshot(null);

    const label = await firstValueFrom(resolver.resolve(snapshot));

    expect(label).toBe('Region');
    expect(regionServiceSpy.getRegion).not.toHaveBeenCalled();
    expect(regionServiceSpy.getCachedRegion).not.toHaveBeenCalled();
  });

  it('uses cached region display name when available', async () => {
    regionServiceSpy.getCachedRegion.and.returnValue(mockRegion);
    const snapshot = createSnapshot(mockRegion.regionOnestopId);

    const label = await firstValueFrom(resolver.resolve(snapshot));

    expect(label).toBe('Montréal, Québec, Canada');
    expect(regionServiceSpy.getCachedRegion).toHaveBeenCalledWith(
      mockRegion.regionOnestopId,
    );
    expect(regionServiceSpy.getRegion).not.toHaveBeenCalled();
  });

  it('fetches region when cache is missing and formats display name', async () => {
    regionServiceSpy.getCachedRegion.and.returnValue(undefined);
    regionServiceSpy.getRegion.and.returnValue(of(mockRegionDetail));
    const snapshot = createSnapshot(mockRegion.regionOnestopId);

    const label = await firstValueFrom(resolver.resolve(snapshot));

    expect(label).toBe('Montréal, Québec, Canada');
    expect(regionServiceSpy.getCachedRegion).toHaveBeenCalledWith(
      mockRegion.regionOnestopId,
    );
    expect(regionServiceSpy.getRegion).toHaveBeenCalledWith(
      mockRegion.regionOnestopId,
    );
  });

  it('falls back to humanized slug when api call fails', async () => {
    regionServiceSpy.getCachedRegion.and.returnValue(undefined);
    regionServiceSpy.getRegion.and.returnValue(
      throwError(() => new Error('boom')),
    );
    const snapshot = createSnapshot(mockRegion.regionOnestopId);

    const label = await firstValueFrom(resolver.resolve(snapshot));

    expect(label).toBe('Montr al qu, Bec, Canada');
    expect(regionServiceSpy.getRegion).toHaveBeenCalledWith(
      mockRegion.regionOnestopId,
    );
  });

  it('humanizes short region slugs', async () => {
    regionServiceSpy.getCachedRegion.and.returnValue(undefined);
    regionServiceSpy.getRegion.and.returnValue(
      throwError(() => new Error('boom')),
    );
    const snapshot = createSnapshot('r-ny');

    const label = await firstValueFrom(resolver.resolve(snapshot));

    expect(label).toBe('Ny');
  });

  it('handles empty slugs by falling back to region id', async () => {
    regionServiceSpy.getCachedRegion.and.returnValue(undefined);
    regionServiceSpy.getRegion.and.returnValue(
      throwError(() => new Error('boom')),
    );
    const snapshot = createSnapshot('r-');

    const label = await firstValueFrom(resolver.resolve(snapshot));

    expect(label).toBe('r-');
  });
});
