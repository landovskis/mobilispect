import { TestBed } from '@angular/core/testing';
import {
  HttpClientTestingModule,
  HttpTestingController,
} from '@angular/common/http/testing';
import { RegionService } from './region.service';
import { environment } from '../../../environments/environment';
import {
  FeedSpecType,
  FeedStatus,
  MetropolitanRegion,
} from '../models/region.models';
import { vi } from 'vitest';

describe('RegionService', () => {
  let service: RegionService;
  let httpMock: HttpTestingController;
  let internals: RegionServiceInternals;

  const baseRegion: MetropolitanRegion = {
    regionOnestopId: 'r-test',
    name: 'Test Region',
    adm0Name: 'United States',
    adm1Name: 'California',
    autoUpdateEnabled: false,
    feedCount: 2,
    lastCheckAt: null,
    createdAt: '2024-01-01T00:00:00Z',
    updatedAt: '2024-01-01T00:00:00Z',
  };

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [HttpClientTestingModule],
      providers: [RegionService],
    });

    service = TestBed.inject(RegionService);
    httpMock = TestBed.inject(HttpTestingController);
    internals = service as unknown as RegionServiceInternals;
  });

  afterEach(() => {
    httpMock.verify();
  });

  it('loads regions, updates cache, and applies filters', () => {
    service.listRegions(true).subscribe((regions) => {
      expect(regions.length).toBe(1);
      expect(regions[0].regionOnestopId).toBe('r-can');
    });

    const req = httpMock.expectOne(
      (request) => request.url === `${environment.apiUrl}/feeds/regions`,
    );
    expect(req.request.params.get('autoUpdateEnabled')).toBe('true');
    req.flush({
      regions: [
        {
          ...baseRegion,
          regionOnestopId: 'r-can',
          name: 'Toronto',
          autoUpdateEnabled: true,
        },
        {
          ...baseRegion,
          regionOnestopId: 'r-us',
          name: 'Austin',
          autoUpdateEnabled: false,
        },
      ],
      total: 2,
    });
  });

  it('serves cached regions without another request', () => {
    internals.regionsCache$.next([
      { ...baseRegion, regionOnestopId: 'r-1', autoUpdateEnabled: true },
      { ...baseRegion, regionOnestopId: 'r-2', autoUpdateEnabled: false },
    ]);
    internals.lastCacheUpdate = Date.now();

    service.listRegions(false).subscribe((regions) => {
      expect(regions.length).toBe(1);
      expect(regions[0].regionOnestopId).toBe('r-2');
    });

    httpMock.expectNone(`${environment.apiUrl}/feeds/regions`);
  });

  it('updates cache on region update', () => {
    internals.regionsCache$.next([
      { ...baseRegion, regionOnestopId: 'r-1', autoUpdateEnabled: false },
      { ...baseRegion, regionOnestopId: 'r-2', autoUpdateEnabled: false },
    ]);

    service
      .updateRegion('r-1', { autoUpdateEnabled: true })
      .subscribe((updated) => {
        expect(updated.autoUpdateEnabled).toBe(true);
      });

    const req = httpMock.expectOne(`${environment.apiUrl}/feeds/regions/r-1`);
    expect(req.request.method).toBe('PATCH');
    req.flush({
      ...baseRegion,
      regionOnestopId: 'r-1',
      autoUpdateEnabled: true,
    });

    const cached = service.getCachedRegion('r-1');
    expect(cached?.autoUpdateEnabled).toBe(true);
  });

  it('lists feeds for a region with query params', () => {
    service
      .listFeedsForRegion('r-1', {
        specType: FeedSpecType.GTFS,
        status: FeedStatus.ACTIVE,
      })
      .subscribe((feeds) => {
        expect(feeds.length).toBe(1);
      });

    const req = httpMock.expectOne(
      (request) =>
        request.url === `${environment.apiUrl}/feeds/regions/r-1/feeds`,
    );
    expect(req.request.params.get('specType')).toBe(FeedSpecType.GTFS);
    expect(req.request.params.get('status')).toBe(FeedStatus.ACTIVE);
    req.flush({
      feeds: [
        {
          feedOnestopId: 'f-1',
          regionOnestopId: 'r-1',
          name: 'Feed 1',
          specType: FeedSpecType.GTFS,
          downloadUrl: 'https://example.com/gtfs.zip',
          currentVersionSha1: null,
          lastCheckedAt: null,
          lastUpdatedAt: null,
          status: FeedStatus.ACTIVE,
          hasAuthentication: false,
          createdAt: '2024-01-01T00:00:00Z',
          updatedAt: '2024-01-01T00:00:00Z',
        },
      ],
      total: 1,
    });
  });

  it('triggers feed discovery requests', () => {
    service.discoverFeedsForRegion('r-1').subscribe((result) => {
      expect(result.feedsDiscovered).toBe(2);
    });

    const req = httpMock.expectOne(
      `${environment.apiUrl}/feeds/regions/r-1/discover`,
    );
    expect(req.request.method).toBe('POST');
    req.flush({
      regionOnestopId: 'r-1',
      feedsDiscovered: 2,
      feedsCreated: 1,
      feedsUpdated: 1,
      errors: [],
    });
  });

  it('supports region searching and ranking helpers', () => {
    internals.regionsCache$.next([
      { ...baseRegion, regionOnestopId: 'r-1', name: 'Toronto', feedCount: 10 },
      { ...baseRegion, regionOnestopId: 'r-2', name: 'Austin', feedCount: 2 },
    ]);
    internals.lastCacheUpdate = Date.now();

    service.searchRegions('tor').subscribe((regions) => {
      expect(regions.length).toBe(1);
      expect(regions[0].regionOnestopId).toBe('r-1');
    });

    service.getTopRegionsByFeedCount(1).subscribe((regions) => {
      expect(regions[0].regionOnestopId).toBe('r-1');
    });
  });

  it('filters regions needing attention and Canadian prioritization', () => {
    vi.useFakeTimers();
    vi.setSystemTime(new Date('2024-06-02T12:00:00Z'));

    internals.regionsCache$.next([
      {
        ...baseRegion,
        regionOnestopId: 'r-can',
        name: 'Toronto',
        lastCheckAt: null,
        feedCount: 5,
      },
      {
        ...baseRegion,
        regionOnestopId: 'r-us',
        name: 'Austin',
        lastCheckAt: '2024-06-02T11:00:00Z',
        feedCount: 2,
      },
    ]);
    internals.lastCacheUpdate = Date.now();

    service.getRegionsNeedingAttention().subscribe((regions) => {
      expect(regions.length).toBe(1);
      expect(regions[0].regionOnestopId).toBe('r-can');
    });

    const sorted = service.sortWithCanadianPriority([
      { ...baseRegion, regionOnestopId: 'r-us', name: 'Austin', feedCount: 20 },
      {
        ...baseRegion,
        regionOnestopId: 'r-can',
        name: 'Toronto',
        feedCount: 1,
      },
    ]);

    expect(sorted[0].regionOnestopId).toBe('r-can');

    vi.useRealTimers();
  });

  it('reports cache validity and clearing', () => {
    internals.regionsCache$.next([baseRegion]);
    internals.lastCacheUpdate = Date.now();
    expect(service.isCacheValid()).toBe(true);

    service.clearCache();
    expect(service.isCacheValid()).toBe(false);
  });

  it('detects Canadian regions by name or id', () => {
    const canadian = service.isCanadianRegion({
      ...baseRegion,
      name: 'Montreal',
      regionOnestopId: 'r-ca',
    });

    const nonCanadian = service.isCanadianRegion({
      ...baseRegion,
      name: 'Austin',
      regionOnestopId: 'r-us',
    });

    expect(canadian).toBe(true);
    expect(nonCanadian).toBe(false);
  });
});

type RegionServiceInternals = {
  regionsCache$: {
    next: (value: MetropolitanRegion[] | null) => void;
    value: MetropolitanRegion[] | null;
  };
  lastCacheUpdate: number;
};
