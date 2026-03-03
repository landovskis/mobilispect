import { TestBed, fakeAsync, tick } from '@angular/core/testing';
import { firstValueFrom, of, throwError } from 'rxjs';
import { vi } from 'vitest';
import { RegionListComponent } from './region-list.component';
import { RegionService } from '../../feeds/services/region.service';
import { ImportService } from '../../feeds/services/import.service';
import { SchedulerService } from '../../feeds/services/scheduler.service';
import {
  FeedImportSummary,
  ImportStatus,
  TriggerType,
} from '../../feeds/models/import.models';
import { MetropolitanRegion } from '../../feeds/models/region.models';
import { MatSnackBar } from '@angular/material/snack-bar';

describe('RegionListComponent', () => {
  let component: RegionListComponent;
  let regionService: RegionService;
  let importService: ImportService;
  let schedulerService: SchedulerService;
  let snackBar: MatSnackBar;

  const baseRegion: MetropolitanRegion = {
    regionOnestopId: 'r-test',
    name: 'Test Region',
    adm0Name: 'United States',
    adm1Name: 'California',
    autoUpdateEnabled: false,
    feedCount: 2,
    lastCheckAt: '2024-01-01T00:00:00Z',
    createdAt: '2024-01-01T00:00:00Z',
    updatedAt: '2024-01-01T00:00:00Z',
  };

  const baseImportSummary: FeedImportSummary = {
    id: 'imp-1',
    feedOnestopId: 'f-1',
    feedName: 'Feed 1',
    regionOnestopId: 'r-test',
    regionName: 'Test Region',
    status: ImportStatus.RUNNING,
    triggerType: TriggerType.MANUAL,
    startedAt: '2024-01-01T00:00:00Z',
    completedAt: null,
    fileSizeBytes: null,
    errorMessage: null,
    progress: null,
  };

  beforeEach(() => {
    regionService = {
      listRegions: vi.fn(),
      clearCache: vi.fn(),
      sortWithCanadianPriority: vi.fn(),
    } as unknown as RegionService;
    importService = {
      getActiveImports: vi.fn(),
      startPollingActiveImports: vi.fn(),
      stopPollingActiveImports: vi.fn(),
      getActiveImportsObservable: vi.fn(),
    } as unknown as ImportService;
    schedulerService = {
      enableFeedAutoUpdate: vi.fn(),
      disableFeedAutoUpdate: vi.fn(),
      checkFeedUpdate: vi.fn(),
      getAllFeedVersions: vi.fn(),
    } as unknown as SchedulerService;
    snackBar = {
      open: vi.fn(),
    } as unknown as MatSnackBar;

    vi.mocked(regionService.listRegions).mockReturnValue(of([baseRegion]));
    vi.mocked(regionService.sortWithCanadianPriority).mockImplementation(
      (regions) => regions,
    );
    vi.mocked(importService.getActiveImports).mockReturnValue(
      of([baseImportSummary]),
    );
    vi.mocked(importService.getActiveImportsObservable).mockReturnValue(
      of([baseImportSummary]),
    );
    vi.mocked(schedulerService.enableFeedAutoUpdate).mockReturnValue(
      of(void 0),
    );
    vi.mocked(schedulerService.disableFeedAutoUpdate).mockReturnValue(
      of(void 0),
    );
    vi.mocked(schedulerService.checkFeedUpdate).mockReturnValue(of(true));
    vi.mocked(schedulerService.getAllFeedVersions).mockReturnValue(of([]));

    TestBed.configureTestingModule({
      imports: [RegionListComponent],
      providers: [
        { provide: RegionService, useValue: regionService },
        { provide: ImportService, useValue: importService },
        { provide: SchedulerService, useValue: schedulerService },
        { provide: MatSnackBar, useValue: snackBar },
      ],
    });
    component = TestBed.createComponent(RegionListComponent).componentInstance;
  });

  it('loads regions and active imports on init', () => {
    component.ngOnInit();

    expect(regionService.listRegions).toHaveBeenCalled();
    expect(importService.getActiveImports).toHaveBeenCalled();
    expect(importService.startPollingActiveImports).toHaveBeenCalled();
    expect(component.regions$.value.length).toBe(1);
    expect(component.activeImports$.value.length).toBe(1);
    expect(component.isLoading$.value).toBe(false);
  });

  it('handles region load errors', () => {
    vi.mocked(regionService.listRegions).mockReturnValue(
      throwError(() => new Error('fail')),
    );

    component.ngOnInit();

    expect(component.isLoading$.value).toBe(false);
    expect(component.error$.value).toBe(
      'Failed to load regions. Please try again.',
    );
  });

  it('filters regions by search term and auto-update flag', fakeAsync(() => {
    const regions: MetropolitanRegion[] = [
      {
        ...baseRegion,
        regionOnestopId: 'r-1',
        name: 'Toronto',
        autoUpdateEnabled: true,
      },
      {
        ...baseRegion,
        regionOnestopId: 'r-2',
        name: 'Austin',
        autoUpdateEnabled: false,
      },
    ];

    let results: MetropolitanRegion[] = [];
    component.filteredRegions$.subscribe((items) => {
      results = items;
    });

    component.regions$.next(regions);
    component.onSearchTermChange('tor');
    component.setAutoUpdateFilter(true);
    tick(300);

    expect(results.length).toBe(1);
    expect(results[0].regionOnestopId).toBe('r-1');
  }));

  it('emits selection and details events', () => {
    let selected: MetropolitanRegion | undefined;
    let details: MetropolitanRegion | undefined;

    component.regionSelected.subscribe((region) => (selected = region));
    component.regionDetailsRequested.subscribe((region) => (details = region));

    component.selectRegion(baseRegion);
    component.viewRegionDetails(baseRegion);

    expect(selected).toEqual(baseRegion);
    expect(details).toEqual(baseRegion);
  });

  it('refreshes regions and resets cache', () => {
    component.refreshRegions();

    expect(regionService.clearCache).toHaveBeenCalled();
    expect(regionService.listRegions).toHaveBeenCalled();
  });

  it('handles discovery completion for selected region', () => {
    component.selectedRegion = baseRegion;
    vi.spyOn(component, 'refreshRegions').mockImplementation(() => {});
    let emitted: MetropolitanRegion | undefined;

    component.regionSelected.subscribe((region) => (emitted = region));

    component.handleDiscoveryCompleted(baseRegion);

    expect(component.refreshRegions).toHaveBeenCalled();
    expect(emitted).toEqual(baseRegion);
  });

  it('calculates active import status', () => {
    component.activeImports$.next([baseImportSummary]);

    expect(component.hasActiveImport(baseRegion)).toBe(true);
    expect(component.getActiveImportCount(baseRegion)).toBe(1);
  });

  it('finds active imports by feed id match', () => {
    component.activeImports$.next([
      {
        ...baseImportSummary,
        regionName: 'Other',
        feedOnestopId: 'f-r-1-demo',
      },
    ]);

    expect(
      component.hasActiveImport({ ...baseRegion, regionOnestopId: 'r-1' }),
    ).toBe(true);
  });

  it('computes total feeds from filtered regions', async () => {
    component.regions$.next([
      { ...baseRegion, regionOnestopId: 'r-1', feedCount: 3 },
      { ...baseRegion, regionOnestopId: 'r-2', feedCount: 5 },
    ]);

    const total = await firstValueFrom(component.getTotalFeeds());
    expect(total).toBe(8);
  });

  it('toggles auto-update and shows snackbar', () => {
    vi.spyOn(component, 'refreshRegions').mockImplementation(() => {});

    component.toggleAutoUpdate(baseRegion, true);

    expect(schedulerService.enableFeedAutoUpdate).toHaveBeenCalledWith(
      'r-test',
    );
    expect(snackBar.open).toHaveBeenCalledWith(
      expect.stringContaining('Automatic updates enabled'),
      'Close',
      { duration: 3000 },
    );
    expect(component.refreshRegions).toHaveBeenCalled();
    expect(component.isUpdatingAutoUpdate.has('r-test')).toBe(false);
    expect(baseRegion.autoUpdateEnabled).toBe(true);
  });

  it('handles auto-update errors', () => {
    vi.mocked(schedulerService.enableFeedAutoUpdate).mockReturnValue(
      throwError(() => new Error('fail')),
    );

    component.toggleAutoUpdate(baseRegion, true);

    expect(component.isUpdatingAutoUpdate.has('r-test')).toBe(false);
    expect(snackBar.open).toHaveBeenCalledWith(
      'Failed to update auto-update setting',
      'Close',
      { duration: 3000 },
    );
  });

  it('checks for updates and stores version status', () => {
    component.checkForUpdates(baseRegion);

    const status = component.getVersionStatus(baseRegion);
    expect(status.hasUpdate).toBe(true);
    expect(status.lastChecked instanceof Date).toBe(true);
  });

  it('returns existing version status data', () => {
    const now = new Date('2024-06-01T12:00:00Z');
    component.feedVersions.set('r-test', { lastChecked: now, hasUpdate: true });

    const status = component.getVersionStatus(baseRegion);

    expect(status.lastChecked).toBe(now);
    expect(status.hasUpdate).toBe(true);
  });

  it('stops polling on destroy', () => {
    component.ngOnDestroy();

    expect(importService.stopPollingActiveImports).toHaveBeenCalled();
  });
});
