import { TestBed } from '@angular/core/testing';
import {
  MatSnackBar,
  MatSnackBarRef,
  SimpleSnackBar,
} from '@angular/material/snack-bar';
import {
  ActivatedRoute,
  ParamMap,
  Router,
  convertToParamMap,
} from '@angular/router';
import { Subject, of, throwError } from 'rxjs';
import { DiscoverRegionsPageComponent } from './discover-regions.page';
import { RegionService } from '../../feeds/services/region.service';
import { ImportService } from '../../feeds/services/import.service';
import { FeedsMetricsService } from '../../feeds/services/feeds-metrics.service';
import { FeedsEventsService } from '../../feeds/services/feeds-events.service';
import {
  Feed,
  FeedImport,
  FeedSpecType,
  FeedStatus,
  ImportStatus,
  MetropolitanRegion,
  TriggerType,
} from '../../feeds/models';

describe('DiscoverRegionsPageComponent', () => {
  let component: DiscoverRegionsPageComponent;
  let regionService: jasmine.SpyObj<RegionService>;
  let importService: jasmine.SpyObj<ImportService>;
  let metrics: jasmine.SpyObj<FeedsMetricsService>;
  let events: FeedsEventsService;
  let router: jasmine.SpyObj<Router>;
  let route: ActivatedRoute;
  let snackBar: jasmine.SpyObj<MatSnackBar>;

  const baseRegion: MetropolitanRegion = {
    regionOnestopId: 'r-1',
    name: 'Test Region',
    adm0Name: 'United States',
    adm1Name: 'California',
    autoUpdateEnabled: false,
    feedCount: 1,
    lastCheckAt: null,
    createdAt: '2024-01-01T00:00:00Z',
    updatedAt: '2024-01-01T00:00:00Z',
  };

  const baseFeed: Feed = {
    feedOnestopId: 'f-1',
    regionOnestopId: 'r-1',
    name: 'Test Feed',
    specType: FeedSpecType.GTFS,
    downloadUrl: 'https://example.com/gtfs.zip',
    currentVersionSha1: null,
    lastCheckedAt: null,
    lastUpdatedAt: null,
    status: FeedStatus.ACTIVE,
    hasAuthentication: false,
    createdAt: '2024-01-01T00:00:00Z',
    updatedAt: '2024-01-01T00:00:00Z',
  };

  beforeEach(() => {
    spyOn(console, 'error');

    regionService = jasmine.createSpyObj<RegionService>('RegionService', [
      'listRegions',
      'listFeedsForRegion',
      'getCachedRegions',
    ]);
    importService = jasmine.createSpyObj<ImportService>('ImportService', [
      'startImport',
      'refreshActiveImports',
    ]);
    metrics = jasmine.createSpyObj<FeedsMetricsService>('FeedsMetricsService', [
      'setSelectedRegion',
      'setDiscoverFeedCount',
      'resetSelectedRegion',
    ]);
    events = new FeedsEventsService();
    router = jasmine.createSpyObj<Router>('Router', ['navigate']);
    snackBar = jasmine.createSpyObj<MatSnackBar>('MatSnackBar', ['open']);

    const queryParamMap$ = new Subject<ParamMap>();
    route = {
      snapshot: {
        queryParamMap: convertToParamMap({ region: 'r-1' }),
      },
      queryParamMap: queryParamMap$.asObservable(),
    } as ActivatedRoute;

    snackBar.open.and.returnValue({
      onAction: () => new Subject<void>(),
    } as MatSnackBarRef<SimpleSnackBar>);

    regionService.listRegions.and.returnValue(of([baseRegion]));
    regionService.getCachedRegions.and.returnValue(of([baseRegion]));
    regionService.listFeedsForRegion.and.returnValue(of([baseFeed]));
    const baseImport: FeedImport = {
      id: 'imp-1',
      feedOnestopId: baseFeed.feedOnestopId,
      administratorId: null,
      administratorUsername: null,
      triggerType: TriggerType.MANUAL,
      status: ImportStatus.PENDING,
      versionSha1: null,
      startedAt: '2024-01-01T00:00:00Z',
      completedAt: null,
      fileSizeBytes: null,
      errorMessage: null,
      createdAt: '2024-01-01T00:00:00Z',
      updatedAt: '2024-01-01T00:00:00Z',
    };
    importService.startImport.and.returnValue(of(baseImport));

    TestBed.configureTestingModule({
      providers: [
        { provide: RegionService, useValue: regionService },
        { provide: ImportService, useValue: importService },
        { provide: MatSnackBar, useValue: snackBar },
        { provide: Router, useValue: router },
        { provide: ActivatedRoute, useValue: route },
        { provide: FeedsMetricsService, useValue: metrics },
        { provide: FeedsEventsService, useValue: events },
      ],
    });
    component = TestBed.runInInjectionContext(
      () => new DiscoverRegionsPageComponent(),
    );
  });

  it('loads regions and bootstraps selection from query params', () => {
    component.ngOnInit();

    expect(component.selectedRegionId).toBe('r-1');
    expect(component.regionFeeds.length).toBe(1);
    expect(component.agencyGroups.length).toBe(1);
    expect(metrics.setSelectedRegion).toHaveBeenCalled();
    expect(metrics.setDiscoverFeedCount).toHaveBeenCalledWith(1);
  });

  it('clears selection when region is empty', () => {
    component.onRegionChange('');

    expect(component.selectedRegionId).toBeNull();
    expect(component.regionFeeds.length).toBe(0);
    expect(metrics.resetSelectedRegion).toHaveBeenCalled();
    expect(metrics.setDiscoverFeedCount).toHaveBeenCalledWith(0);
  });

  it('handles region changes and loads feeds', () => {
    component.regions = [baseRegion];
    component.onRegionChange('r-1');

    expect(component.selectedRegionId).toBe('r-1');
    expect(regionService.listFeedsForRegion).toHaveBeenCalledWith('r-1');
    expect(router.navigate).toHaveBeenCalledWith(['/regions/discover', 'r-1']);
  });

  it('starts region imports and navigates to imports view', () => {
    component.regionFeeds = [baseFeed];
    component.selectedRegion = baseRegion;
    component.selectedRegionId = baseRegion.regionOnestopId;
    component.importRegion();

    expect(importService.startImport).toHaveBeenCalledWith('f-1');
    expect(importService.refreshActiveImports).toHaveBeenCalled();
    expect(router.navigate).toHaveBeenCalledWith(['/feeds/imports']);
  });

  it('handles import failures with error toast', () => {
    importService.startImport.and.returnValue(
      throwError(() => ({ message: 'fail' })),
    );
    component.regionFeeds = [baseFeed];
    component.selectedRegion = baseRegion;
    component.selectedRegionId = baseRegion.regionOnestopId;

    component.importRegion();

    expect(snackBar.open).toHaveBeenCalled();
  });

  it('surfaces backend error details on import failure', () => {
    importService.startImport.and.returnValue(
      throwError(() => ({ error: { message: 'backend down' } })),
    );
    component.regionFeeds = [baseFeed];
    component.selectedRegion = baseRegion;
    component.selectedRegionId = baseRegion.regionOnestopId;

    component.importRegion();

    const message = (snackBar.open.calls.mostRecent().args[0] as string) || '';
    expect(message).toContain('backend down');
  });

  it('refreshes data when events fire', () => {
    const loadRegionsSpy = spyOn(
      component as unknown as { loadRegions: () => void },
      'loadRegions',
    );
    const loadFeedsSpy = spyOn(
      component as unknown as { loadFeedsForRegion: (id: string) => void },
      'loadFeedsForRegion',
    );
    component.selectedRegionId = 'r-1';
    component.ngOnInit();

    events.triggerRefresh();

    expect(loadRegionsSpy).toHaveBeenCalled();
    expect(loadFeedsSpy).toHaveBeenCalledWith('r-1');
  });

  it('shows an error toast when regions fail to load', () => {
    regionService.listRegions.and.returnValue(
      throwError(() => new Error('fail')),
    );

    component.ngOnInit();

    expect(snackBar.open).toHaveBeenCalledWith(
      'Failed to load regions',
      'Close',
      { duration: 3000 },
    );
  });

  it('retries loading feeds when the snackbar action fires', () => {
    const action$ = new Subject<void>();
    snackBar.open.and.returnValue({
      onAction: () => action$,
    } as MatSnackBarRef<SimpleSnackBar>);
    regionService.listFeedsForRegion.and.returnValue(
      throwError(() => new Error('fail')),
    );

    component.onRegionChange('r-1');

    expect(snackBar.open).toHaveBeenCalled();
    expect(regionService.listFeedsForRegion).toHaveBeenCalledTimes(1);

    regionService.listFeedsForRegion.and.returnValue(of([baseFeed]));
    action$.next();

    expect(regionService.listFeedsForRegion).toHaveBeenCalledTimes(2);
  });

  it('clears selection when query param is removed', () => {
    const queryParamMap$ = new Subject<ParamMap>();
    route = {
      snapshot: {
        queryParamMap: convertToParamMap({}),
      },
      queryParamMap: queryParamMap$.asObservable(),
    } as unknown as ActivatedRoute;

    TestBed.configureTestingModule({
      providers: [
        { provide: RegionService, useValue: regionService },
        { provide: ImportService, useValue: importService },
        { provide: MatSnackBar, useValue: snackBar },
        { provide: Router, useValue: router },
        { provide: ActivatedRoute, useValue: route },
        { provide: FeedsMetricsService, useValue: metrics },
        { provide: FeedsEventsService, useValue: events },
      ],
    });
    component = TestBed.runInInjectionContext(
      () => new DiscoverRegionsPageComponent(),
    );

    component.ngOnInit();
    expect(component.selectedRegionId).toBeNull();

    component.selectedRegionId = 'r-1';
    queryParamMap$.next({ get: () => null });

    expect(component.selectedRegionId).toBeNull();
  });

  it('formats region display names', () => {
    const displayName = (
      component as unknown as {
        getRegionDisplayName: (
          region: MetropolitanRegion | null | undefined,
        ) => string | null;
      }
    ).getRegionDisplayName(baseRegion);
    expect(displayName).toBe('Test Region, California, United States');

    expect(
      (
        component as unknown as {
          getRegionDisplayName: (
            region: MetropolitanRegion | null | undefined,
          ) => string | null;
        }
      ).getRegionDisplayName(null),
    ).toBeNull();
  });
});
