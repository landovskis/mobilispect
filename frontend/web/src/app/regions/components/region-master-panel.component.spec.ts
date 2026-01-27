import {
  ComponentFixture,
  TestBed,
  fakeAsync,
  tick,
} from '@angular/core/testing';
import { NoopAnimationsModule } from '@angular/platform-browser/animations';
import { MatSnackBar } from '@angular/material/snack-bar';
import { of, throwError, BehaviorSubject, take, skip, filter } from 'rxjs';
import { RegionMasterPanelComponent } from './region-master-panel.component';
import { RegionService } from '../../feeds/services/region.service';
import { ImportService } from '../../feeds/services/import.service';
import { SchedulerService } from '../../feeds/services/scheduler.service';
import { MetropolitanRegion } from '../../feeds/models/region.models';
import { FeedImportSummary } from '../../feeds/models/import.models';

describe('RegionMasterPanelComponent', () => {
  let component: RegionMasterPanelComponent;
  let fixture: ComponentFixture<RegionMasterPanelComponent>;
  let mockRegionService: jasmine.SpyObj<RegionService>;
  let mockImportService: jasmine.SpyObj<ImportService>;
  let mockSchedulerService: jasmine.SpyObj<SchedulerService>;
  let mockSnackBar: jasmine.SpyObj<MatSnackBar>;

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
      regionOnestopId: 'r-toronto',
      regionName: 'Toronto',
      status: 'IN_PROGRESS' as any,
      triggerType: 'MANUAL' as any,
      startedAt: '2024-01-03T10:00:00Z',
      completedAt: null,
      fileSizeBytes: null,
      errorMessage: null,
      progress: null,
    },
  ];

  beforeEach(async () => {
    mockRegionService = jasmine.createSpyObj('RegionService', [
      'listRegions',
      'clearCache',
      'sortWithCanadianPriority',
    ]);
    mockImportService = jasmine.createSpyObj('ImportService', [
      'getActiveImports',
      'startPollingActiveImports',
      'stopPollingActiveImports',
      'getActiveImportsObservable',
    ]);
    mockSchedulerService = jasmine.createSpyObj('SchedulerService', [
      'enableFeedAutoUpdate',
      'disableFeedAutoUpdate',
    ]);
    mockSnackBar = jasmine.createSpyObj('MatSnackBar', ['open']);

    // Setup default return values
    mockRegionService.listRegions.and.returnValue(of(mockRegions));
    mockRegionService.sortWithCanadianPriority.and.callFake(
      (regions) => regions,
    );
    mockImportService.getActiveImports.and.returnValue(of(mockActiveImports));
    mockImportService.getActiveImportsObservable.and.returnValue(
      new BehaviorSubject(mockActiveImports).asObservable(),
    );

    await TestBed.configureTestingModule({
      imports: [RegionMasterPanelComponent, NoopAnimationsModule],
      providers: [
        { provide: RegionService, useValue: mockRegionService },
        { provide: ImportService, useValue: mockImportService },
        { provide: SchedulerService, useValue: mockSchedulerService },
        { provide: MatSnackBar, useValue: mockSnackBar },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(RegionMasterPanelComponent);
    component = fixture.componentInstance;
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  describe('initialization', () => {
    it('should load regions on init', () => {
      fixture.detectChanges();

      expect(mockRegionService.listRegions).toHaveBeenCalled();
    });

    it('should start polling active imports on init', () => {
      fixture.detectChanges();

      expect(mockImportService.startPollingActiveImports).toHaveBeenCalled();
    });

    it('should subscribe to active imports observable', () => {
      fixture.detectChanges();

      expect(mockImportService.getActiveImportsObservable).toHaveBeenCalled();
    });

    it('should populate regions$ with loaded data', (done) => {
      fixture.detectChanges();

      component.regions$.pipe(take(1)).subscribe((regions) => {
        expect(regions).toEqual(mockRegions);
        done();
      });
    });

    it('should set isLoading$ to false after loading', (done) => {
      fixture.detectChanges();

      setTimeout(() => {
        component.isLoading$.pipe(take(1)).subscribe((loading) => {
          expect(loading).toBe(false);
          done();
        });
      }, 100);
    });
  });

  describe('search functionality', () => {
    it('should debounce search input by 300ms', fakeAsync(() => {
      fixture.detectChanges();

      component.onSearchTermChange('tor');
      tick(100);
      expect(component.searchTerm).toBe('');

      tick(200);
      expect(component.searchTerm).toBe('tor');
    }));

    it('should filter regions by name', fakeAsync(() => {
      fixture.detectChanges();

      component.onSearchTermChange('toronto');
      tick(300);

      component.filteredRegions$.pipe(skip(1), take(1)).subscribe((regions) => {
        expect(regions.length).toBe(1);
        expect(regions[0].name).toBe('Toronto');
      });
    }));

    it('should filter regions by onestop ID', fakeAsync(() => {
      fixture.detectChanges();

      component.onSearchTermChange('r-test-vancouver');
      tick(300);

      component.filteredRegions$.pipe(skip(1), take(1)).subscribe((regions) => {
        expect(regions.length).toBe(1);
        expect(regions[0].regionOnestopId).toBe('r-test-vancouver');
      });
    }));

    it('should filter regions by administrative area', fakeAsync(() => {
      fixture.detectChanges();

      component.onSearchTermChange('washington');
      tick(300);

      component.filteredRegions$.pipe(skip(1), take(1)).subscribe((regions) => {
        expect(regions.length).toBe(1);
        expect(regions[0].adm1Name).toBe('Washington');
      });
    }));

    it('should be case-insensitive', fakeAsync(() => {
      fixture.detectChanges();

      component.onSearchTermChange('TORONTO');
      tick(300);

      component.filteredRegions$.pipe(skip(1), take(1)).subscribe((regions) => {
        expect(regions.length).toBe(1);
        expect(regions[0].name).toBe('Toronto');
      });
    }));

    it('should return all regions when search is cleared', fakeAsync(() => {
      fixture.detectChanges();

      component.onSearchTermChange('toronto');
      tick(300);

      component.onSearchTermChange('');
      tick(300);

      component.filteredRegions$.pipe(skip(1), take(1)).subscribe((regions) => {
        expect(regions.length).toBe(3);
      });
    }));
  });

  describe('filter functionality', () => {
    it('should filter regions with auto-update enabled', (done) => {
      fixture.detectChanges();

      component.setAutoUpdateFilter(true);

      component.filteredRegions$.pipe(skip(1), take(1)).subscribe((regions) => {
        expect(regions.length).toBe(2);
        expect(regions.every((r) => r.autoUpdateEnabled)).toBe(true);
        done();
      });
    });

    it('should filter regions with auto-update disabled', (done) => {
      fixture.detectChanges();

      component.setAutoUpdateFilter(false);

      component.filteredRegions$.pipe(skip(1), take(1)).subscribe((regions) => {
        expect(regions.length).toBe(1);
        expect(regions.every((r) => !r.autoUpdateEnabled)).toBe(true);
        done();
      });
    });

    it('should show all regions when filter is undefined', (done) => {
      fixture.detectChanges();

      component.setAutoUpdateFilter(undefined);

      component.filteredRegions$.pipe(skip(1), take(1)).subscribe((regions) => {
        expect(regions.length).toBe(3);
        done();
      });
    });

    it('should combine search and filter', fakeAsync(() => {
      fixture.detectChanges();

      component.onSearchTermChange('canada');
      tick(300);
      component.setAutoUpdateFilter(true);

      component.filteredRegions$
        .pipe(
          filter((regions) => regions.length === 1),
          take(1),
        )
        .subscribe((regions) => {
          expect(regions.length).toBe(1);
          expect(regions[0].name).toBe('Toronto');
        });
    }));
  });

  describe('region selection', () => {
    it('should emit regionSelected event when region is selected', () => {
      spyOn(component.regionSelected, 'emit');
      fixture.detectChanges();

      component.selectRegion(mockRegions[0]);

      expect(component.regionSelected.emit).toHaveBeenCalledWith(
        mockRegions[0],
      );
    });

    it('should update selectedRegion property', () => {
      fixture.detectChanges();

      component.selectRegion(mockRegions[0]);

      expect(component.selectedRegion).toEqual(mockRegions[0]);
    });

    it('should emit regionDetailsRequested when details are requested', () => {
      spyOn(component.regionDetailsRequested, 'emit');
      fixture.detectChanges();

      component.viewRegionDetails(mockRegions[0]);

      expect(component.regionDetailsRequested.emit).toHaveBeenCalledWith(
        mockRegions[0],
      );
    });
  });

  describe('auto-update functionality', () => {
    it('should enable auto-update for a region', () => {
      mockSchedulerService.enableFeedAutoUpdate.and.returnValue(of(undefined));
      mockSnackBar.open.and.returnValue({ onAction: () => of(null) } as any);
      fixture.detectChanges();

      component.toggleAutoUpdate(mockRegions[1], true);

      expect(mockSchedulerService.enableFeedAutoUpdate).toHaveBeenCalledWith(
        'r-test-vancouver',
      );
    });

    it('should disable auto-update for a region', () => {
      mockSchedulerService.disableFeedAutoUpdate.and.returnValue(of(undefined));
      mockSnackBar.open.and.returnValue({ onAction: () => of(null) } as any);
      fixture.detectChanges();

      component.toggleAutoUpdate(mockRegions[0], false);

      expect(mockSchedulerService.disableFeedAutoUpdate).toHaveBeenCalledWith(
        'r-test-toronto',
      );
    });

    it('should show success message when auto-update is enabled', () => {
      mockSchedulerService.enableFeedAutoUpdate.and.returnValue(of(undefined));
      mockSnackBar.open.and.returnValue({ onAction: () => of(null) } as any);
      fixture.detectChanges();

      component.toggleAutoUpdate(mockRegions[1], true);

      expect(mockSnackBar.open).toHaveBeenCalledWith(
        jasmine.stringContaining('enabled'),
        'Close',
        { duration: 3000 },
      );
    });

    it('should show success message when auto-update is disabled', () => {
      mockSchedulerService.disableFeedAutoUpdate.and.returnValue(of(undefined));
      mockSnackBar.open.and.returnValue({ onAction: () => of(null) } as any);
      fixture.detectChanges();

      component.toggleAutoUpdate(mockRegions[0], false);

      expect(mockSnackBar.open).toHaveBeenCalledWith(
        jasmine.stringContaining('disabled'),
        'Close',
        { duration: 3000 },
      );
    });

    it('should refresh regions after auto-update toggle', () => {
      mockSchedulerService.enableFeedAutoUpdate.and.returnValue(of(undefined));
      mockSnackBar.open.and.returnValue({ onAction: () => of(null) } as any);
      spyOn(component, 'refreshRegions');
      fixture.detectChanges();

      component.toggleAutoUpdate(mockRegions[1], true);

      expect(component.refreshRegions).toHaveBeenCalled();
    });

    it('should handle auto-update toggle errors', () => {
      mockSchedulerService.enableFeedAutoUpdate.and.returnValue(
        throwError(() => new Error('Failed to update')),
      );
      mockSnackBar.open.and.returnValue({ onAction: () => of(null) } as any);
      spyOn(console, 'error');
      fixture.detectChanges();

      component.toggleAutoUpdate(mockRegions[1], true);

      expect(console.error).toHaveBeenCalled();
      expect(mockSnackBar.open).toHaveBeenCalledWith(
        'Failed to update auto-update setting',
        'Close',
        { duration: 3000 },
      );
    });

    it('should track updating state', () => {
      mockSchedulerService.enableFeedAutoUpdate.and.returnValue(of(undefined));
      mockSnackBar.open.and.returnValue({ onAction: () => of(null) } as any);
      fixture.detectChanges();

      expect(component.isUpdatingAutoUpdate.has('r-test-vancouver')).toBe(
        false,
      );

      component.toggleAutoUpdate(mockRegions[1], true);

      expect(component.isUpdatingAutoUpdate.has('r-test-vancouver')).toBe(
        false,
      );
    });
  });

  describe('active imports', () => {
    it('should calculate active import count for region', () => {
      component.activeImports$.next(mockActiveImports);

      const count = component.getActiveImportCount(mockRegions[0]);

      expect(count).toBe(1);
    });

    it('should return 0 when no active imports for region', () => {
      component.activeImports$.next(mockActiveImports);

      const count = component.getActiveImportCount(mockRegions[1]);

      expect(count).toBe(0);
    });
  });

  describe('statistics', () => {
    it('should calculate total feeds across filtered regions', (done) => {
      fixture.detectChanges();

      component
        .getTotalFeeds()
        .pipe(take(1))
        .subscribe((total) => {
          expect(total).toBe(25); // 12 + 8 + 5
          done();
        });
    });

    it('should update total feeds when filters change', fakeAsync(() => {
      fixture.detectChanges();

      component.setAutoUpdateFilter(true);
      tick(100);

      component
        .getTotalFeeds()
        .pipe(skip(1), take(1))
        .subscribe((total) => {
          expect(total).toBe(17); // 12 + 5 (only auto-update enabled)
        });
    }));
  });

  describe('refresh functionality', () => {
    it('should clear cache and reload regions', () => {
      fixture.detectChanges();

      component.refreshRegions();

      expect(mockRegionService.clearCache).toHaveBeenCalled();
      expect(mockRegionService.listRegions).toHaveBeenCalled();
    });
  });

  describe('error handling', () => {
    it('should display error message when region loading fails', (done) => {
      mockRegionService.listRegions.and.returnValue(
        throwError(() => new Error('Network error')),
      );
      spyOn(console, 'error');

      fixture.detectChanges();

      setTimeout(() => {
        component.error$.pipe(take(1)).subscribe((error) => {
          expect(error).toBe('Failed to load regions. Please try again.');
          done();
        });
      }, 100);
    });

    it('should set isLoading to false on error', (done) => {
      mockRegionService.listRegions.and.returnValue(
        throwError(() => new Error('Network error')),
      );

      fixture.detectChanges();

      setTimeout(() => {
        component.isLoading$.pipe(take(1)).subscribe((loading) => {
          expect(loading).toBe(false);
          done();
        });
      }, 100);
    });
  });

  describe('utility methods', () => {
    it('should format last check time', () => {
      const formatted = component.formatLastCheck(mockRegions[0]);

      expect(formatted).toBeTruthy();
      expect(typeof formatted).toBe('string');
    });

    it('should get display name for region', () => {
      const displayName = component.getDisplayName(mockRegions[0]);

      expect(displayName).toBeTruthy();
      expect(typeof displayName).toBe('string');
    });
  });

  describe('component lifecycle', () => {
    it('should stop polling imports on destroy', () => {
      fixture.detectChanges();

      component.ngOnDestroy();

      expect(mockImportService.stopPollingActiveImports).toHaveBeenCalled();
    });

    it('should complete destroy$ subject on destroy', () => {
      fixture.detectChanges();
      const destroySpy = spyOn(component['destroy$'], 'next');
      const completeSpy = spyOn(component['destroy$'], 'complete');

      component.ngOnDestroy();

      expect(destroySpy).toHaveBeenCalled();
      expect(completeSpy).toHaveBeenCalled();
    });
  });

  describe('template rendering', () => {
    it('should display loading spinner when loading', () => {
      fixture.detectChanges();

      component.isLoading$.next(true);
      fixture.detectChanges();

      const spinner = fixture.nativeElement.querySelector('mat-spinner');
      expect(spinner).toBeTruthy();
    });

    it('should display error message when error occurs', () => {
      fixture.detectChanges();

      component.isLoading$.next(false);
      component.error$.next('Test error message');
      fixture.detectChanges();

      const errorContainer =
        fixture.nativeElement.querySelector('.error-container');
      expect(errorContainer).toBeTruthy();
      expect(errorContainer.textContent).toContain('Test error message');
    });

    it('should display empty state when no regions found', () => {
      fixture.detectChanges();

      component.regions$.next([]);
      component.isLoading$.next(false);
      component.error$.next(null);
      fixture.detectChanges();

      const emptyState = fixture.nativeElement.querySelector('.empty-state');
      expect(emptyState).toBeTruthy();
    });

    it('should display region cards when regions are loaded', () => {
      fixture.detectChanges();

      const regionCards =
        fixture.nativeElement.querySelectorAll('.region-card');
      expect(regionCards.length).toBeGreaterThan(0);
    });

    it('should display quick stats', () => {
      fixture.detectChanges();

      const quickStats = fixture.nativeElement.querySelector('.quick-stats');
      expect(quickStats).toBeTruthy();
    });
  });
});
