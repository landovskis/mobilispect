import { ComponentFixture, TestBed } from '@angular/core/testing';
import { SimpleChange } from '@angular/core';
import { MatSnackBar } from '@angular/material/snack-bar';
import { NoopAnimationsModule } from '@angular/platform-browser/animations';
import { ActivatedRoute } from '@angular/router';
import { EMPTY, of, Subject, throwError, firstValueFrom } from 'rxjs';
import { RegionDetailPanelComponent } from './region-detail-panel.component';
import { RegionService } from '../../feeds/services/region.service';
import { ImportService } from '../../feeds/services/import.service';
import { AgencyService } from '../../agencies/services/agency.service';
import { FeedsMetricsService } from '../../feeds/services/feeds-metrics.service';
import { FeedsEventsService } from '../../feeds/services/feeds-events.service';
import {
  Feed,
  FeedSpecType,
  FeedStatus,
  MetropolitanRegion,
  MetropolitanRegionDetail,
} from '../../feeds/models/region.models';
import { RegionImportStatus } from '../../feeds/models/import.models';
import { vi } from 'vitest';

describe('RegionDetailPanelComponent', () => {
  let component: RegionDetailPanelComponent;
  let fixture: ComponentFixture<RegionDetailPanelComponent>;
  let mockRegionService: RegionService;
  let mockImportService: ImportService;
  let mockAgencyService: AgencyService;
  let mockMetricsService: FeedsMetricsService;
  let mockEventsService: FeedsEventsService;
  let mockSnackBar: MatSnackBar;

  const mockRegion: MetropolitanRegion = {
    regionOnestopId: 'r-test-toronto',
    name: 'Toronto',
    adm0Name: 'Canada',
    adm1Name: 'Ontario',
    feedCount: 12,
    autoUpdateEnabled: true,
    createdAt: '2024-01-01T00:00:00Z',
    updatedAt: '2024-01-02T00:00:00Z',
    lastCheckAt: null,
  };

  const mockRegionDetail: MetropolitanRegionDetail = {
    ...mockRegion,
    feeds: [],
  };

  const mockFeeds: Feed[] = [
    {
      feedOnestopId: 'f-test-feed1',
      regionOnestopId: 'r-test-toronto',
      name: 'TTC Feed',
      specType: FeedSpecType.GTFS,
      downloadUrl: 'https://example.com/feed1.zip',
      currentVersionSha1: null,
      lastCheckedAt: null,
      lastUpdatedAt: null,
      status: FeedStatus.ACTIVE,
      hasAuthentication: false,
      createdAt: '2024-01-01T00:00:00Z',
      updatedAt: '2024-01-02T00:00:00Z',
    },
    {
      feedOnestopId: 'f-test-feed2',
      regionOnestopId: 'r-test-toronto',
      name: 'GO Transit Feed',
      specType: FeedSpecType.GTFS,
      downloadUrl: 'https://example.com/feed2.zip',
      currentVersionSha1: null,
      lastCheckedAt: null,
      lastUpdatedAt: null,
      status: FeedStatus.ACTIVE,
      hasAuthentication: false,
      createdAt: '2024-01-01T00:00:00Z',
      updatedAt: '2024-01-02T00:00:00Z',
    },
  ];

  const mockAgencies = {
    content: [
      {
        id: 'agency-1',
        name: 'Toronto Transit Commission',
        feedOnestopId: 'f-test-feed1',
        regionIds: ['r-test-toronto'],
        routeCount: 150,
        activeRouteCount: 150,
        routesByType: { BUS: 100, SUBWAY: 50 } as Record<string, number>,
      },
      {
        id: 'agency-2',
        name: 'GO Transit',
        feedOnestopId: 'f-test-feed2',
        regionIds: ['r-test-toronto'],
        routeCount: 50,
        activeRouteCount: 50,
        routesByType: { RAIL: 50 } as Record<string, number>,
      },
    ],
    totalElements: 2,
    totalPages: 1,
    number: 0,
    size: 100,
  };

  const snackBarRef = {
    onAction: () => new Subject<void>(),
  };

  const setRegion = (region: MetropolitanRegion | null) => {
    component.region = region;
    component.ngOnChanges({
      region: new SimpleChange(null, region, true),
    });
  };

  beforeEach(async () => {
    mockRegionService = {
      getRegion: vi.fn(),
      listFeedsForRegion: vi.fn(),
    } as unknown as RegionService;
    mockImportService = {
      startImport: vi.fn(),
      startPollingActiveImports: vi.fn(),
      refreshActiveImports: vi.fn(),
      importAllFeedsForRegion: vi.fn(),
      getActiveRegionImport: vi.fn(),
      monitorRegionImportProgress: vi.fn(),
      getActiveImportsObservable: vi.fn(),
      cancelImport: vi.fn(),
    } as unknown as ImportService;
    mockAgencyService = {
      listAgencies: vi.fn(),
    } as unknown as AgencyService;
    mockMetricsService = {
      setSelectedRegion: vi.fn(),
      setDiscoverFeedCount: vi.fn(),
    } as unknown as FeedsMetricsService;
    mockEventsService = {} as unknown as FeedsEventsService;
    mockSnackBar = {
      open: vi.fn(),
    } as unknown as MatSnackBar;

    vi.mocked(mockImportService.getActiveImportsObservable).mockReturnValue(of([]));
    vi.mocked(mockRegionService.getRegion).mockReturnValue(of(mockRegionDetail));
    vi.mocked(mockRegionService.listFeedsForRegion).mockReturnValue(of(mockFeeds));
    vi.mocked(mockAgencyService.listAgencies).mockReturnValue(of(mockAgencies));
    vi.mocked(mockImportService.getActiveRegionImport).mockReturnValue(of(null));
    vi.mocked(mockImportService.monitorRegionImportProgress).mockReturnValue(EMPTY);
    vi.mocked(mockSnackBar.open).mockReturnValue(snackBarRef as any);

    TestBed.overrideComponent(RegionDetailPanelComponent, {
      set: {
        providers: [{ provide: MatSnackBar, useValue: mockSnackBar }],
      },
    });

    await TestBed.configureTestingModule({
      imports: [RegionDetailPanelComponent, NoopAnimationsModule],
      providers: [
        { provide: RegionService, useValue: mockRegionService },
        { provide: ImportService, useValue: mockImportService },
        { provide: AgencyService, useValue: mockAgencyService },
        { provide: FeedsMetricsService, useValue: mockMetricsService },
        { provide: FeedsEventsService, useValue: mockEventsService },
        { provide: MatSnackBar, useValue: mockSnackBar },
        {
          provide: ActivatedRoute,
          useValue: {
            snapshot: { paramMap: { get: () => null } },
          },
        },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(RegionDetailPanelComponent);
    component = fixture.componentInstance;
  });

  it('creates the component', () => {
    expect(component).toBeTruthy();
  });

  it('renders empty state when no region selected', () => {
    component.region = null;
    fixture.detectChanges();

    const emptyState = fixture.nativeElement.querySelector('.empty-state');
    expect(emptyState).toBeTruthy();
    expect(emptyState.textContent).toContain('No Region Selected');
  });

  it('loads data and metrics when region is set', () => {
    setRegion(mockRegion);

    expect(mockRegionService.listFeedsForRegion).toHaveBeenCalledWith('r-test-toronto');
    expect(mockRegionService.getRegion).toHaveBeenCalledWith('r-test-toronto');
    expect(mockAgencyService.listAgencies).toHaveBeenCalledWith(0, 100, 'r-test-toronto');
    expect(mockImportService.getActiveRegionImport).toHaveBeenCalledWith('r-test-toronto');
    expect(mockImportService.startPollingActiveImports).toHaveBeenCalled();
    expect(mockImportService.refreshActiveImports).toHaveBeenCalled();
    expect(mockMetricsService.setSelectedRegion).toHaveBeenCalledWith(
      'r-test-toronto',
      expect.any(String)
    );
  });

  it('resets import state when region cleared', () => {
    setRegion(mockRegion);
    component.regionImportStatus = {
      regionImportId: 'import-1',
      regionOnestopId: 'r-test-toronto',
      status: RegionImportStatus.RUNNING,
      startedAt: '2024-01-01T00:00:00Z',
      updatedAt: '2024-01-01T00:10:00Z',
      totalFeeds: 2,
      completedFeeds: 0,
      failedFeeds: 0,
    } as any;
    component.regionImportLoading = true;

    component.region = null;
    component.ngOnChanges({
      region: new SimpleChange(mockRegion, null, false),
    });

    expect(component.regionImportStatus).toBeNull();
    expect(component.regionImportLoading).toBe(false);
  });

  it('groups feeds and updates metrics after feed load', () => {
    setRegion(mockRegion);

    expect(component.regionFeeds).toEqual(mockFeeds);
    expect(component.agencyGroups.length).toBeGreaterThan(0);
    expect(mockMetricsService.setDiscoverFeedCount).toHaveBeenCalledWith(mockFeeds.length);
  });

  it('shows retry snackbar when feed load fails', () => {
    vi.mocked(mockRegionService.listFeedsForRegion).mockReturnValue(
      throwError(() => new Error('Network error'))
    );

    setRegion(mockRegion);

    expect(mockSnackBar.open).toHaveBeenCalledWith(
      expect.stringContaining('Failed to load feeds'),
      'Retry',
      expect.any(Object)
    );
  });

  it('computes summary and sorts agencies', async () => {
    setRegion(mockRegion);

    const summary = await firstValueFrom(component.summary$);
    expect(summary).toEqual(
      expect.objectContaining({
        name: 'Toronto',
        totalAgencies: 2,
        totalActiveRoutes: 200,
      })
    );

    const agencies = await firstValueFrom(component.agencies$);
    const names = agencies.content.map((agency) => agency.name);
    expect(names).toEqual(['GO Transit', 'Toronto Transit Commission']);
  });

  it('starts region import monitoring when active import is running', () => {
    vi.mocked(mockImportService.getActiveRegionImport).mockReturnValue(
      of({
        regionImportId: 'import-1',
        regionOnestopId: 'r-test-toronto',
        status: RegionImportStatus.RUNNING,
        startedAt: '2024-01-01T00:00:00Z',
        updatedAt: '2024-01-01T00:10:00Z',
        totalFeeds: 2,
        completedFeeds: 0,
        failedFeeds: 0,
      } as any)
    );

    setRegion(mockRegion);

    expect(mockImportService.monitorRegionImportProgress).toHaveBeenCalledWith(
      'import-1',
      expect.any(Object)
    );
  });

  it('renders region header and meta when region is selected', () => {
    component.region = mockRegion;
    fixture.detectChanges();

    const header = fixture.nativeElement.querySelector('.region-header');
    expect(header).toBeTruthy();
    expect(header.textContent).toContain('Toronto');

    const meta = fixture.nativeElement.querySelector('.region-meta');
    expect(meta).toBeTruthy();
    expect(meta.textContent).toContain('r-test-toronto');
  });

  it('handles import errors with retry snackbar', () => {
    vi.mocked(mockImportService.startImport).mockReturnValue(
      throwError(() => ({ error: { message: 'Import failed' } }))
    );

    component.importFeed(mockFeeds[0]);

    expect(mockSnackBar.open).toHaveBeenCalledWith(
      expect.stringContaining('Import failed'),
      'Retry',
      expect.any(Object)
    );
  });

  it('cancels import and shows success toast', () => {
    vi.mocked(mockImportService.cancelImport).mockReturnValue(of({ importId: 'import-1' } as any));

    component.cancelImport('import-1');

    expect(mockSnackBar.open).toHaveBeenCalledWith(
      expect.stringContaining('Import cancelled successfully'),
      'Close',
      { duration: 3000 }
    );
  });
});
