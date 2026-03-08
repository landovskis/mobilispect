import { ComponentFixture, TestBed, fakeAsync, tick } from '@angular/core/testing';
import { NoopAnimationsModule } from '@angular/platform-browser/animations';
import { MatSnackBar } from '@angular/material/snack-bar';
import { of, throwError } from 'rxjs';
import { vi } from 'vitest';
import { RegionMasterPanelComponent } from './region-master-panel.component';
import { RegionService } from '../../feeds/services/region.service';
import { ImportService } from '../../feeds/services/import.service';
import { SchedulerService } from '../../feeds/services/scheduler.service';
import { MetropolitanRegion } from '../../feeds/models/region.models';
import { FeedImportSummary, ImportStatus, TriggerType } from '../../feeds/models/import.models';

describe('RegionMasterPanelComponent', () => {
  let component: RegionMasterPanelComponent;
  let fixture: ComponentFixture<RegionMasterPanelComponent>;
  let regionService: RegionService;
  let importService: ImportService;
  let schedulerService: SchedulerService;
  let snackBar: MatSnackBar;

  const mockRegions: MetropolitanRegion[] = [
    {
      regionOnestopId: 'r-test-toronto',
      name: 'Toronto',
      adm0Name: 'Canada',
      adm1Name: 'Ontario',
      feedCount: 12,
      autoUpdateEnabled: true,
      createdAt: '2024-01-01T00:00:00Z',
      updatedAt: '2024-01-02T00:00:00Z',
      lastCheckAt: '2024-01-03T00:00:00Z',
    },
    {
      regionOnestopId: 'r-test-vancouver',
      name: 'Vancouver',
      adm0Name: 'Canada',
      adm1Name: 'British Columbia',
      feedCount: 8,
      autoUpdateEnabled: false,
      createdAt: '2024-01-01T00:00:00Z',
      updatedAt: '2024-01-02T00:00:00Z',
      lastCheckAt: null,
    },
    {
      regionOnestopId: 'r-test-seattle',
      name: 'Seattle',
      adm0Name: 'United States',
      adm1Name: 'Washington',
      feedCount: 5,
      autoUpdateEnabled: true,
      createdAt: '2024-01-01T00:00:00Z',
      updatedAt: '2024-01-02T00:00:00Z',
      lastCheckAt: null,
    },
  ];

  const mockActiveImports: FeedImportSummary[] = [
    {
      id: 'import-1',
      feedOnestopId: 'f-test-feed1',
      feedName: 'Test Feed 1',
      regionOnestopId: 'r-test-toronto',
      regionName: 'Toronto',
      status: ImportStatus.RUNNING,
      triggerType: TriggerType.MANUAL,
      startedAt: '2024-01-03T10:00:00Z',
      completedAt: null,
      fileSizeBytes: null,
      errorMessage: null,
      progress: null,
    },
  ];

  const setup = () => {
    component.ngOnInit();
  };

  beforeEach(async () => {
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
    } as unknown as SchedulerService;
    snackBar = {
      open: vi.fn(),
    } as unknown as MatSnackBar;

    vi.mocked(regionService.listRegions).mockReturnValue(of(mockRegions));
    vi.mocked(regionService.sortWithCanadianPriority).mockImplementation((regions) => regions);
    vi.mocked(importService.getActiveImports).mockReturnValue(of(mockActiveImports));
    vi.mocked(importService.getActiveImportsObservable).mockReturnValue(of(mockActiveImports));
    vi.mocked(schedulerService.enableFeedAutoUpdate).mockReturnValue(of(undefined));
    vi.mocked(schedulerService.disableFeedAutoUpdate).mockReturnValue(of(undefined));

    await TestBed.configureTestingModule({
      imports: [RegionMasterPanelComponent, NoopAnimationsModule],
      providers: [
        { provide: RegionService, useValue: regionService },
        { provide: ImportService, useValue: importService },
        { provide: SchedulerService, useValue: schedulerService },
        { provide: MatSnackBar, useValue: snackBar },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(RegionMasterPanelComponent);
    component = fixture.componentInstance;
  });

  it('loads regions and active imports on init', () => {
    setup();

    expect(regionService.listRegions).toHaveBeenCalled();
    expect(importService.getActiveImports).toHaveBeenCalled();
    expect(importService.startPollingActiveImports).toHaveBeenCalled();
    expect(importService.getActiveImportsObservable).toHaveBeenCalled();
    expect(component.regions$.value).toEqual(mockRegions);
    expect(component.activeImports$.value).toEqual(mockActiveImports);
    expect(component.isLoading$.value).toBe(false);
  });

  it('updates search term with debounce', fakeAsync(() => {
    setup();

    component.onSearchTermChange('tor');
    tick(299);
    expect(component.searchTerm).toBe('');

    tick(1);
    expect(component.searchTerm).toBe('tor');
  }));

  it('filters regions by search and auto-update flag', fakeAsync(() => {
    setup();

    let results: MetropolitanRegion[] = [];
    component.filteredRegions$.subscribe((items) => {
      results = items;
    });

    component.onSearchTermChange('canada');
    component.setAutoUpdateFilter(true);
    tick(300);

    expect(results.length).toBe(1);
    expect(results[0].regionOnestopId).toBe('r-test-toronto');
  }));

  it('emits selection and details events', () => {
    let selected: MetropolitanRegion | undefined;
    let details: MetropolitanRegion | undefined;

    component.regionSelected.subscribe((region) => (selected = region));
    component.regionDetailsRequested.subscribe((region) => (details = region));

    component.selectRegion(mockRegions[0]);
    component.viewRegionDetails(mockRegions[1]);

    expect(selected).toEqual(mockRegions[0]);
    expect(details).toEqual(mockRegions[1]);
  });

  it('refreshes regions and clears cache', () => {
    component.refreshRegions();

    expect(regionService.clearCache).toHaveBeenCalled();
    expect(regionService.listRegions).toHaveBeenCalled();
  });

  it('toggles auto-update and refreshes regions', () => {
    vi.spyOn(component, 'refreshRegions').mockImplementation(() => {});

    component.toggleAutoUpdate(mockRegions[1], true);

    expect(schedulerService.enableFeedAutoUpdate).toHaveBeenCalledWith('r-test-vancouver');
    expect(snackBar.open).toHaveBeenCalledWith(expect.stringContaining('enabled'), 'Close', {
      duration: 3000,
    });
    expect(component.refreshRegions).toHaveBeenCalled();
    expect(component.isUpdatingAutoUpdate.has('r-test-vancouver')).toBe(false);
  });

  it('handles auto-update errors', () => {
    vi.mocked(schedulerService.enableFeedAutoUpdate).mockReturnValue(
      throwError(() => new Error('Failed to update'))
    );

    component.toggleAutoUpdate(mockRegions[1], true);

    expect(component.isUpdatingAutoUpdate.has('r-test-vancouver')).toBe(false);
    expect(snackBar.open).toHaveBeenCalledWith('Failed to update auto-update setting', 'Close', {
      duration: 3000,
    });
  });

  it('calculates active import count', () => {
    component.activeImports$.next(mockActiveImports);

    expect(component.getActiveImportCount(mockRegions[0])).toBe(1);
    expect(component.getActiveImportCount(mockRegions[1])).toBe(0);
  });

  it('handles region load errors', () => {
    vi.mocked(regionService.listRegions).mockReturnValue(
      throwError(() => new Error('Network error'))
    );

    setup();

    expect(component.error$.value).toBe('Failed to load regions. Please try again.');
    expect(component.isLoading$.value).toBe(false);
  });

  it('stops polling on destroy', () => {
    setup();
    component.ngOnDestroy();

    expect(importService.stopPollingActiveImports).toHaveBeenCalled();
  });
});
