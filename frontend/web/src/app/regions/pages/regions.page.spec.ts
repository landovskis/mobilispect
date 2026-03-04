import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute, Router, convertToParamMap } from '@angular/router';
import { BehaviorSubject, of, throwError } from 'rxjs';
import { RegionsPageComponent } from './regions.page';
import { RegionService } from '../../feeds/services/region.service';
import { ImportService } from '../../feeds/services/import.service';
import { SchedulerService } from '../../feeds/services/scheduler.service';
import { MetropolitanRegion } from '../../feeds/models/region.models';
import { NoopAnimationsModule } from '@angular/platform-browser/animations';
import { MatSnackBar } from '@angular/material/snack-bar';
import { AgencyService } from '../../agencies/services/agency.service';
import { FeedsMetricsService } from '../../feeds/services/feeds-metrics.service';
import { FeedsEventsService } from '../../feeds/services/feeds-events.service';
import { vi } from 'vitest';

describe('RegionsPageComponent', () => {
  let component: RegionsPageComponent;
  let fixture: ComponentFixture<RegionsPageComponent>;
  let mockRegionService: RegionService;
  let mockImportService: ImportService;
  let mockSchedulerService: SchedulerService;
  let mockSnackBar: MatSnackBar;
  let mockAgencyService: AgencyService;
  let mockMetricsService: FeedsMetricsService;
  let mockEventsService: FeedsEventsService;
  let mockRouter: Router;
  let paramMapSubject: BehaviorSubject<any>;
  let queryParamMapSubject: BehaviorSubject<any>;

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

  const mockRegionDetail = {
    ...mockRegion,
    feeds: [],
  };

  beforeEach(async () => {
    mockRegionService = {
      getRegion: vi.fn(),
      listRegions: vi.fn(),
      listFeedsForRegion: vi.fn(),
      sortWithCanadianPriority: vi.fn(),
      clearCache: vi.fn(),
    } as unknown as RegionService;
    mockImportService = {
      getActiveImports: vi.fn(),
      startPollingActiveImports: vi.fn(),
      stopPollingActiveImports: vi.fn(),
      getActiveImportsObservable: vi.fn(),
      refreshActiveImports: vi.fn(),
      getActiveRegionImport: vi.fn(),
      monitorRegionImportProgress: vi.fn(),
      startImport: vi.fn(),
      importAllFeedsForRegion: vi.fn(),
      cancelImport: vi.fn(),
    } as unknown as ImportService;
    mockSchedulerService = {
      enableFeedAutoUpdate: vi.fn(),
      disableFeedAutoUpdate: vi.fn(),
    } as unknown as SchedulerService;
    mockSnackBar = {
      open: vi.fn(),
    } as unknown as MatSnackBar;
    mockAgencyService = {
      listAgencies: vi.fn(),
    } as unknown as AgencyService;
    mockMetricsService = {
      setSelectedRegion: vi.fn(),
      setDiscoverFeedCount: vi.fn(),
    } as unknown as FeedsMetricsService;
    mockEventsService = {
      '': undefined,
    } as unknown as FeedsEventsService;
    mockRouter = {
      navigate: vi.fn(),
    } as unknown as Router;

    // Setup default return values
    vi.mocked(mockRegionService.listRegions).mockReturnValue(of([mockRegion]));
    vi.mocked(mockRegionService.listFeedsForRegion).mockReturnValue(of([]));
    vi.mocked(mockRegionService.sortWithCanadianPriority).mockImplementation(
      (regions) => regions,
    );
    vi.mocked(mockImportService.getActiveImports).mockReturnValue(of([]));
    vi.mocked(mockImportService.getActiveImportsObservable).mockReturnValue(
      new BehaviorSubject([]).asObservable(),
    );
    vi.mocked(mockImportService.getActiveRegionImport).mockReturnValue(
      of(null),
    );
    vi.mocked(mockImportService.monitorRegionImportProgress).mockReturnValue(
      of(null as any),
    );
    vi.mocked(mockImportService.startImport).mockReturnValue(of({} as any));
    vi.mocked(mockImportService.importAllFeedsForRegion).mockReturnValue(
      of({
        totalFeeds: 0,
        startedCount: 0,
        failedCount: 0,
        skippedCount: 0,
      } as any),
    );
    vi.mocked(mockImportService.cancelImport).mockReturnValue(of({} as any));
    vi.mocked(mockAgencyService.listAgencies).mockReturnValue(
      of({ content: [], totalElements: 0, totalPages: 0 } as any),
    );
    vi.mocked(mockSnackBar.open).mockReturnValue({
      onAction: () => of(null),
    } as any);

    // Create subjects for route params
    paramMapSubject = new BehaviorSubject(convertToParamMap({}));
    queryParamMapSubject = new BehaviorSubject(convertToParamMap({}));

    await TestBed.configureTestingModule({
      imports: [RegionsPageComponent, NoopAnimationsModule],
      providers: [
        { provide: RegionService, useValue: mockRegionService },
        { provide: ImportService, useValue: mockImportService },
        { provide: SchedulerService, useValue: mockSchedulerService },
        { provide: MatSnackBar, useValue: mockSnackBar },
        { provide: AgencyService, useValue: mockAgencyService },
        { provide: FeedsMetricsService, useValue: mockMetricsService },
        { provide: FeedsEventsService, useValue: mockEventsService },
        { provide: Router, useValue: mockRouter },
        {
          provide: ActivatedRoute,
          useValue: {
            paramMap: paramMapSubject.asObservable(),
            queryParamMap: queryParamMapSubject.asObservable(),
            snapshot: {
              paramMap: convertToParamMap({}),
              queryParamMap: convertToParamMap({}),
            },
          },
        },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(RegionsPageComponent);
    component = fixture.componentInstance;
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  describe('initialization', () => {
    it('should initialize with no selected region', () => {
      fixture.detectChanges();

      expect(component.selectedRegion$.value).toBeNull();
    });

    it('should subscribe to route parameter changes on init', () => {
      vi.spyOn((component as any)['route'].paramMap, 'pipe').mockReturnValue(
        of(convertToParamMap({})),
      );

      component.ngOnInit();

      expect((component as any)['route'].paramMap.pipe).toHaveBeenCalled();
    });

    it('should subscribe to query parameter changes on init', () => {
      vi.spyOn(
        (component as any)['route'].queryParamMap,
        'pipe',
      ).mockReturnValue(of(convertToParamMap({})));

      component.ngOnInit();

      expect((component as any)['route'].queryParamMap.pipe).toHaveBeenCalled();
    });
  });

  describe('route parameter handling', () => {
    it('should load region when regionId param is present', () => {
      vi.mocked(mockRegionService.getRegion).mockReturnValue(
        of(mockRegionDetail),
      );

      fixture.detectChanges();
      paramMapSubject.next(convertToParamMap({ regionId: 'r-test-toronto' }));

      expect(mockRegionService.getRegion).toHaveBeenCalledWith(
        'r-test-toronto',
      );
    });

    it('should update selectedRegion$ when region loads successfully', () => {
      vi.mocked(mockRegionService.getRegion).mockReturnValue(
        of(mockRegionDetail),
      );

      fixture.detectChanges();
      paramMapSubject.next(convertToParamMap({ regionId: 'r-test-toronto' }));

      expect(component.selectedRegion$.value).toEqual(mockRegionDetail);
    });

    it('should clear selectedRegion$ when regionId param is removed', () => {
      vi.mocked(mockRegionService.getRegion).mockReturnValue(
        of(mockRegionDetail),
      );

      fixture.detectChanges();
      paramMapSubject.next(convertToParamMap({ regionId: 'r-test-toronto' }));
      expect(component.selectedRegion$.value).toEqual(mockRegionDetail);

      paramMapSubject.next(convertToParamMap({}));
      expect(component.selectedRegion$.value).toBeNull();
    });

    it('should handle region loading errors gracefully', () => {
      vi.mocked(mockRegionService.getRegion).mockReturnValue(
        throwError(() => new Error('Failed to load region')),
      );
      vi.spyOn(console, 'error').mockImplementation(() => {});

      fixture.detectChanges();
      paramMapSubject.next(convertToParamMap({ regionId: 'r-test-toronto' }));

      expect(console.error).toHaveBeenCalledWith(
        'Failed to load region:',
        expect.any(Error),
      );
      expect(component.selectedRegion$.value).toBeNull();
    });
  });

  describe('query parameter handling (backwards compatibility)', () => {
    it('should redirect when region query param is present and no path param', () => {
      fixture.detectChanges();
      queryParamMapSubject.next(
        convertToParamMap({ region: 'r-test-toronto' }),
      );

      expect(mockRouter.navigate).toHaveBeenCalledWith(
        ['/regions', 'r-test-toronto'],
        { replaceUrl: true },
      );
    });

    it('should not redirect when both path and query params are present', () => {
      vi.mocked(mockRegionService.getRegion).mockReturnValue(
        of(mockRegionDetail),
      );

      // Set route snapshot to have regionId
      const route = TestBed.inject(ActivatedRoute);
      (route.snapshot.paramMap as any) = convertToParamMap({
        regionId: 'r-test-toronto',
      });

      fixture.detectChanges();
      queryParamMapSubject.next(
        convertToParamMap({ region: 'r-test-toronto' }),
      );

      // Navigate should only be called once (from paramMap), not from queryParam
      expect(mockRouter.navigate).not.toHaveBeenCalled();
    });

    it('should not redirect when query param is absent', () => {
      fixture.detectChanges();
      queryParamMapSubject.next(convertToParamMap({}));

      expect(mockRouter.navigate).not.toHaveBeenCalled();
    });
  });

  describe('region selection', () => {
    it('should navigate to region detail when region is selected', () => {
      component.onRegionSelected(mockRegion);

      expect(mockRouter.navigate).toHaveBeenCalledWith([
        '/regions',
        'r-test-toronto',
      ]);
    });

    it('should navigate to region detail when details are requested', () => {
      component.onRegionDetailsRequested(mockRegion);

      expect(mockRouter.navigate).toHaveBeenCalledWith([
        '/regions',
        'r-test-toronto',
      ]);
    });
  });

  describe('component lifecycle', () => {
    it('should complete destroy$ subject on destroy', () => {
      const destroySpy = vi.spyOn((component as any)['destroy$'], 'next');
      const completeSpy = vi.spyOn((component as any)['destroy$'], 'complete');

      component.ngOnDestroy();

      expect(destroySpy).toHaveBeenCalled();
      expect(completeSpy).toHaveBeenCalled();
    });

    it('should unsubscribe from observables on destroy', () => {
      vi.mocked(mockRegionService.getRegion).mockReturnValue(
        of(mockRegionDetail),
      );

      fixture.detectChanges();
      paramMapSubject.next(convertToParamMap({ regionId: 'r-test-toronto' }));

      const subscription = (component as any)['destroy$'].subscribe();
      expect(subscription.closed).toBe(false);

      component.ngOnDestroy();

      // After destroy, the subscription should be closed
      expect(subscription.closed).toBe(true);
    });
  });

  describe('template rendering', () => {
    it('should render master and detail panels', () => {
      fixture.detectChanges();

      const compiled = fixture.nativeElement;
      const masterPanel = compiled.querySelector('.master-panel');
      const detailPanel = compiled.querySelector('.detail-panel');

      expect(masterPanel).toBeTruthy();
      expect(detailPanel).toBeTruthy();
    });

    it('should pass selectedRegion to master panel', () => {
      vi.mocked(mockRegionService.getRegion).mockReturnValue(
        of(mockRegionDetail),
      );

      fixture.detectChanges();
      paramMapSubject.next(convertToParamMap({ regionId: 'r-test-toronto' }));
      fixture.detectChanges();

      const masterPanel = fixture.debugElement.query(
        (el) => el.name === 'app-region-master-panel',
      );

      expect(masterPanel).toBeTruthy();
    });

    it('should pass selectedRegion to detail panel', () => {
      vi.mocked(mockRegionService.getRegion).mockReturnValue(
        of(mockRegionDetail),
      );

      fixture.detectChanges();
      paramMapSubject.next(convertToParamMap({ regionId: 'r-test-toronto' }));
      fixture.detectChanges();

      const detailPanel = fixture.debugElement.query(
        (el) => el.name === 'app-region-detail-panel',
      );

      expect(detailPanel).toBeTruthy();
    });

    it('should apply has-selection class when region is selected', () => {
      vi.mocked(mockRegionService.getRegion).mockReturnValue(
        of(mockRegionDetail),
      );

      fixture.detectChanges();
      paramMapSubject.next(convertToParamMap({ regionId: 'r-test-toronto' }));
      fixture.detectChanges();

      const container =
        fixture.nativeElement.querySelector('.regions-container');
      expect(container.classList.contains('has-selection')).toBe(true);
    });

    it('should not apply has-selection class when no region is selected', () => {
      fixture.detectChanges();

      const container =
        fixture.nativeElement.querySelector('.regions-container');
      expect(container.classList.contains('has-selection')).toBe(false);
    });
  });

  describe('responsive layout', () => {
    it('should have master-detail-layout class', () => {
      fixture.detectChanges();

      const container = fixture.nativeElement.querySelector(
        '.master-detail-layout',
      );
      expect(container).toBeTruthy();
    });

    it('should have master-panel and detail-panel elements', () => {
      fixture.detectChanges();

      const masterPanel = fixture.nativeElement.querySelector('.master-panel');
      const detailPanel = fixture.nativeElement.querySelector('.detail-panel');

      expect(masterPanel).toBeTruthy();
      expect(detailPanel).toBeTruthy();
    });
  });
});
