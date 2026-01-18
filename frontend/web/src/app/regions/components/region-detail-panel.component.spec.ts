import { ComponentFixture, TestBed } from '@angular/core/testing';
import { SimpleChange } from '@angular/core';
import { Router } from '@angular/router';
import { MatSnackBar } from '@angular/material/snack-bar';
import { NoopAnimationsModule } from '@angular/platform-browser/animations';
import { of, throwError } from 'rxjs';
import { RegionDetailPanelComponent } from './region-detail-panel.component';
import { RegionService } from '../../feeds/services/region.service';
import { ImportService } from '../../feeds/services/import.service';
import { AgencyService } from '../../agencies/services/agency.service';
import { FeedsMetricsService } from '../../feeds/services/feeds-metrics.service';
import { FeedsEventsService } from '../../feeds/services/feeds-events.service';
import { MetropolitanRegion, MetropolitanRegionDetail, Feed } from '../../feeds/models/region.models';

describe('RegionDetailPanelComponent', () => {
  let component: RegionDetailPanelComponent;
  let fixture: ComponentFixture<RegionDetailPanelComponent>;
  let mockRegionService: jasmine.SpyObj<RegionService>;
  let mockImportService: jasmine.SpyObj<ImportService>;
  let mockAgencyService: jasmine.SpyObj<AgencyService>;
  let mockMetricsService: jasmine.SpyObj<FeedsMetricsService>;
  let mockEventsService: jasmine.SpyObj<FeedsEventsService>;
  let mockSnackBar: jasmine.SpyObj<MatSnackBar>;
  let mockRouter: jasmine.SpyObj<Router>;

  const mockRegion: MetropolitanRegion = {
    regionOnestopId: 'r-test-toronto',
    name: 'Toronto',
    adm0Name: 'Canada',
    adm1Name: 'Ontario',
    feedCount: 12,
    autoUpdateEnabled: true,
    createdAt: '2024-01-01T00:00:00Z',
    updatedAt: '2024-01-02T00:00:00Z',
    lastCheckAt: null
  };

  const mockRegionDetail: MetropolitanRegionDetail = {
    ...mockRegion,
    feeds: []
  };

  const mockFeeds: Feed[] = [
    {
      feedOnestopId: 'f-test-feed1',
      regionOnestopId: 'r-test-toronto' as any,
      name: 'TTC Feed',
      specType: 'GTFS' as any,
      downloadUrl: 'https://example.com/feed1.zip',
      currentVersionSha1: null,
      lastCheckedAt: null,
      lastUpdatedAt: null,
      status: 'ACTIVE' as any,
      hasAuthentication: false,
      createdAt: '2024-01-01T00:00:00Z',
      updatedAt: '2024-01-02T00:00:00Z'
    },
    {
      feedOnestopId: 'f-test-feed2',
      regionOnestopId: 'r-test-toronto' as any,
      name: 'GO Transit Feed',
      specType: 'GTFS' as any,
      downloadUrl: 'https://example.com/feed2.zip',
      currentVersionSha1: null,
      lastCheckedAt: null,
      lastUpdatedAt: null,
      status: 'ACTIVE' as any,
      hasAuthentication: false,
      createdAt: '2024-01-01T00:00:00Z',
      updatedAt: '2024-01-02T00:00:00Z'
    }
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
        routesByType: { BUS: 100, SUBWAY: 50 } as Record<string, number>
      },
      {
        id: 'agency-2',
        name: 'GO Transit',
        feedOnestopId: 'f-test-feed2',
        regionIds: ['r-test-toronto'],
        routeCount: 50,
        activeRouteCount: 50,
        routesByType: { RAIL: 50 } as Record<string, number>
      }
    ],
    totalElements: 2,
    totalPages: 1,
    number: 0,
    size: 100
  };

  beforeEach(async () => {
    mockRegionService = jasmine.createSpyObj('RegionService', ['getRegion', 'listFeedsForRegion']);
    mockImportService = jasmine.createSpyObj('ImportService', [
      'startImport',
      'refreshActiveImports',
      'importAllFeedsForRegion',
      'getActiveRegionImport',
      'monitorRegionImportProgress'
    ]);
    mockAgencyService = jasmine.createSpyObj('AgencyService', ['listAgencies']);
    mockMetricsService = jasmine.createSpyObj('FeedsMetricsService', [
      'setSelectedRegion',
      'setDiscoverFeedCount'
    ]);
    mockEventsService = jasmine.createSpyObj('FeedsEventsService', ['']);
    mockSnackBar = jasmine.createSpyObj('MatSnackBar', ['open']);
    mockRouter = jasmine.createSpyObj('Router', ['navigate']);

    // Setup default return values
    mockRegionService.getRegion.and.returnValue(of(mockRegionDetail));
    mockRegionService.listFeedsForRegion.and.returnValue(of(mockFeeds));
    mockAgencyService.listAgencies.and.returnValue(of(mockAgencies));
    mockImportService.getActiveRegionImport.and.returnValue(of(null));
    mockImportService.monitorRegionImportProgress.and.returnValue(of(null as any));
    mockSnackBar.open.and.returnValue({ onAction: () => of(null) } as any);

    await TestBed.configureTestingModule({
      imports: [
        RegionDetailPanelComponent,
        NoopAnimationsModule
      ],
      providers: [
        { provide: RegionService, useValue: mockRegionService },
        { provide: ImportService, useValue: mockImportService },
        { provide: AgencyService, useValue: mockAgencyService },
        { provide: FeedsMetricsService, useValue: mockMetricsService },
        { provide: FeedsEventsService, useValue: mockEventsService },
        { provide: MatSnackBar, useValue: mockSnackBar },
        { provide: Router, useValue: mockRouter }
      ]
    }).compileComponents();

    fixture = TestBed.createComponent(RegionDetailPanelComponent);
    component = fixture.componentInstance;
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  describe('empty state', () => {
    it('should display empty state when no region is selected', () => {
      component.region = null;
      fixture.detectChanges();

      const emptyState = fixture.nativeElement.querySelector('.empty-state');
      expect(emptyState).toBeTruthy();
      expect(emptyState.textContent).toContain('No Region Selected');
    });

    it('should not display region content when no region is selected', () => {
      component.region = null;
      fixture.detectChanges();

      const regionHeader = fixture.nativeElement.querySelector('.region-header');
      expect(regionHeader).toBeFalsy();
    });
  });

  describe('region changes', () => {
    it('should load feeds when region is set', () => {
      component.ngOnChanges({
        region: new SimpleChange(null, mockRegion, true)
      });

      expect(mockRegionService.listFeedsForRegion).toHaveBeenCalledWith('r-test-toronto');
    });

    it('should load overview data when region is set', () => {
      component.ngOnChanges({
        region: new SimpleChange(null, mockRegion, true)
      });

      expect(mockRegionService.getRegion).toHaveBeenCalledWith('r-test-toronto');
      expect(mockAgencyService.listAgencies).toHaveBeenCalledWith(0, 100, 'r-test-toronto');
    });

    it('should load active region import status when region is set', () => {
      component.ngOnChanges({
        region: new SimpleChange(null, mockRegion, true)
      });

      expect(mockImportService.getActiveRegionImport).toHaveBeenCalledWith('r-test-toronto');
    });

    it('should update metrics when region is set', () => {
      component.ngOnChanges({
        region: new SimpleChange(null, mockRegion, true)
      });

      expect(mockMetricsService.setSelectedRegion).toHaveBeenCalledWith(
        'r-test-toronto',
        jasmine.any(String)
      );
    });

    it('should not load data when region is null', () => {
      component.ngOnChanges({
        region: new SimpleChange(mockRegion, null, false)
      });

      expect(mockRegionService.listFeedsForRegion).not.toHaveBeenCalled();
      expect(mockRegionService.getRegion).not.toHaveBeenCalled();
    });

    it('should not load data when changes do not include region', () => {
      component.ngOnChanges({});

      expect(mockRegionService.listFeedsForRegion).not.toHaveBeenCalled();
    });
  });

  describe('feeds tab', () => {
    beforeEach(() => {
      component.region = mockRegion;
      component.ngOnChanges({
        region: new SimpleChange(null, mockRegion, true)
      });
    });

    it('should load feeds for region', () => {
      expect(mockRegionService.listFeedsForRegion).toHaveBeenCalledWith('r-test-toronto');
    });

    it('should group feeds by agency', (done) => {
      setTimeout(() => {
        expect(component.agencyGroups.length).toBeGreaterThan(0);
        done();
      }, 100);
    });

    it('should update feed count metric', () => {
      expect(mockMetricsService.setDiscoverFeedCount).toHaveBeenCalledWith(mockFeeds.length);
    });

    it('should display loading spinner while loading feeds', () => {
      component.loadingFeeds = true;
      fixture.detectChanges();

      const spinner = fixture.nativeElement.querySelector('mat-spinner');
      expect(spinner).toBeTruthy();
    });

    it('should handle feed loading errors', () => {
      mockRegionService.listFeedsForRegion.and.returnValue(
        throwError(() => new Error('Network error'))
      );
      spyOn(console, 'error');

      component.ngOnChanges({
        region: new SimpleChange(null, mockRegion, true)
      });

      expect(console.error).toHaveBeenCalled();
      expect(mockSnackBar.open).toHaveBeenCalled();
    });

    it('should allow retry on feed loading error', () => {
      mockRegionService.listFeedsForRegion.and.returnValue(
        throwError(() => new Error('Network error'))
      );
      const snackBarRef = {
        onAction: () => of(null)
      };
      mockSnackBar.open.and.returnValue(snackBarRef as any);
      spyOn(snackBarRef, 'onAction').and.returnValue(of(null));

      component.ngOnChanges({
        region: new SimpleChange(null, mockRegion, true)
      });

      expect(mockSnackBar.open).toHaveBeenCalledWith(
        jasmine.stringContaining('Failed'),
        'Retry',
        jasmine.any(Object)
      );
    });
  });

  describe('overview tab', () => {
    beforeEach(() => {
      component.region = mockRegion;
      component.ngOnChanges({
        region: new SimpleChange(null, mockRegion, true)
      });
    });

    it('should load region detail', () => {
      expect(mockRegionService.getRegion).toHaveBeenCalledWith('r-test-toronto');
    });

    it('should load agencies for region', () => {
      expect(mockAgencyService.listAgencies).toHaveBeenCalledWith(0, 100, 'r-test-toronto');
    });

    it('should calculate summary statistics', (done) => {
      setTimeout(() => {
        component.summary$.subscribe(summary => {
          expect(summary!.totalAgencies).toBe(2);
          expect(summary!.totalActiveRoutes).toBe(200); // 150 + 50
          done();
        });
      }, 100);
    });

    it('should sort agencies alphabetically', (done) => {
      setTimeout(() => {
        component.agencies$.subscribe(response => {
          const names = response.content.map(a => a.name);
          expect(names).toEqual(names.slice().sort());
          done();
        });
      }, 100);
    });

    it('should display loading spinner while loading overview', () => {
      component.loadingOverview = true;
      fixture.detectChanges();

      const spinner = fixture.nativeElement.querySelector('mat-spinner');
      expect(spinner).toBeTruthy();
    });
  });

  describe('feed import', () => {
    beforeEach(() => {
      component.region = mockRegion;
      fixture.detectChanges();
    });

    it('should start import for single feed', () => {
      mockImportService.startImport.and.returnValue(of({ importId: 'import-123' } as any));

      component.importFeed(mockFeeds[0]);

      expect(mockImportService.startImport).toHaveBeenCalledWith('f-test-feed1');
    });

    it('should show success message on import start', () => {
      mockImportService.startImport.and.returnValue(of({ importId: 'import-123' } as any));

      component.importFeed(mockFeeds[0]);

      expect(mockSnackBar.open).toHaveBeenCalledWith(
        jasmine.stringContaining('Import started'),
        'Close',
        { duration: 3000 }
      );
    });

    it('should navigate to imports page after starting import', () => {
      mockImportService.startImport.and.returnValue(of({ importId: 'import-123' } as any));

      component.importFeed(mockFeeds[0]);

      expect(mockRouter.navigate).toHaveBeenCalledWith(['/feeds/imports']);
    });

    it('should refresh active imports after starting import', () => {
      mockImportService.startImport.and.returnValue(of({ importId: 'import-123' } as any));

      component.importFeed(mockFeeds[0]);

      expect(mockImportService.refreshActiveImports).toHaveBeenCalled();
    });

    it('should handle import errors', () => {
      mockImportService.startImport.and.returnValue(
        throwError(() => ({ error: { message: 'Import failed' } }))
      );
      spyOn(console, 'error');

      component.importFeed(mockFeeds[0]);

      expect(console.error).toHaveBeenCalled();
      expect(mockSnackBar.open).toHaveBeenCalledWith(
        jasmine.stringContaining('Import failed'),
        'Retry',
        jasmine.any(Object)
      );
    });

    it('should allow retry on import error', () => {
      mockImportService.startImport.and.returnValue(
        throwError(() => new Error('Network error'))
      );
      const snackBarRef = {
        onAction: () => of(null)
      };
      mockSnackBar.open.and.returnValue(snackBarRef as any);
      spyOn(snackBarRef, 'onAction').and.returnValue(of(null));
      spyOn(component, 'importFeed');

      component.importFeed(mockFeeds[0]);

      expect(mockSnackBar.open).toHaveBeenCalledWith(
        jasmine.stringContaining('failed'),
        'Retry',
        jasmine.any(Object)
      );
    });

    it('should import multiple feeds', () => {
      mockImportService.startImport.and.returnValue(of({ importId: 'import-123' } as any));
      spyOn(component, 'importFeed');

      component.importMultipleFeeds(mockFeeds);

      expect(component.importFeed).toHaveBeenCalledTimes(2);
      expect(component.importFeed).toHaveBeenCalledWith(mockFeeds[0]);
      expect(component.importFeed).toHaveBeenCalledWith(mockFeeds[1]);
    });
  });

  describe('feed details', () => {
    it('should show message when viewing feed details', () => {
      component.viewFeedDetails(mockFeeds[0]);

      expect(mockSnackBar.open).toHaveBeenCalledWith(
        jasmine.stringContaining('Viewing details'),
        'Close',
        { duration: 2000 }
      );
    });
  });

  describe('utility methods', () => {
    it('should get display name for region', () => {
      const displayName = component.getDisplayName(mockRegion);

      expect(displayName).toBeTruthy();
      expect(typeof displayName).toBe('string');
    });

    it('should return null for null region', () => {
      const displayName = component.getDisplayName(null);

      expect(displayName).toBeNull();
    });

    it('should return null for undefined region', () => {
      const displayName = component.getDisplayName(undefined);

      expect(displayName).toBeNull();
    });
  });

  describe('component lifecycle', () => {
    it('should complete destroy$ subject on destroy', () => {
      fixture.detectChanges();
      const destroySpy = spyOn(component['destroy$'], 'next');
      const completeSpy = spyOn(component['destroy$'], 'complete');

      component.ngOnDestroy();

      expect(destroySpy).toHaveBeenCalled();
      expect(completeSpy).toHaveBeenCalled();
    });

    it('should unsubscribe from observables on destroy', () => {
      component.region = mockRegion;
      component.ngOnChanges({
        region: new SimpleChange(null, mockRegion, true)
      });
      fixture.detectChanges();

      const subscription = component['destroy$'].subscribe();
      expect(subscription.closed).toBe(false);

      component.ngOnDestroy();

      expect(subscription.closed).toBe(true);
    });
  });

  describe('template rendering', () => {
    it('should display region header when region is selected', () => {
      component.region = mockRegion;
      fixture.detectChanges();

      const header = fixture.nativeElement.querySelector('.region-header');
      expect(header).toBeTruthy();
      expect(header.textContent).toContain('Toronto');
    });

    it('should display region metadata', () => {
      component.region = mockRegion;
      fixture.detectChanges();

      const meta = fixture.nativeElement.querySelector('.region-meta');
      expect(meta).toBeTruthy();
      expect(meta.textContent).toContain('r-test-toronto');
    });

    it('should display tabs when region is selected', () => {
      component.region = mockRegion;
      fixture.detectChanges();

      const tabs = fixture.nativeElement.querySelector('mat-tab-group');
      expect(tabs).toBeTruthy();
    });

    it('should have Feeds tab', () => {
      component.region = mockRegion;
      fixture.detectChanges();

      const tabLabels = fixture.nativeElement.querySelectorAll('.mat-mdc-tab');
      const feedsTab = Array.from(tabLabels).find((tab: any) =>
        tab.textContent.includes('Feeds')
      );
      expect(feedsTab).toBeTruthy();
    });

    it('should have Overview tab', () => {
      component.region = mockRegion;
      fixture.detectChanges();

      const tabLabels = fixture.nativeElement.querySelectorAll('.mat-mdc-tab');
      const overviewTab = Array.from(tabLabels).find((tab: any) =>
        tab.textContent.includes('Overview')
      );
      expect(overviewTab).toBeTruthy();
    });
  });
});
