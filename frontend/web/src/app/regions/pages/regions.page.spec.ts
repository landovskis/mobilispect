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

describe('RegionsPageComponent', () => {
  let component: RegionsPageComponent;
  let fixture: ComponentFixture<RegionsPageComponent>;
  let mockRegionService: jasmine.SpyObj<RegionService>;
  let mockImportService: jasmine.SpyObj<ImportService>;
  let mockSchedulerService: jasmine.SpyObj<SchedulerService>;
  let mockSnackBar: jasmine.SpyObj<MatSnackBar>;
  let mockAgencyService: jasmine.SpyObj<AgencyService>;
  let mockMetricsService: jasmine.SpyObj<FeedsMetricsService>;
  let mockEventsService: jasmine.SpyObj<FeedsEventsService>;
  let mockRouter: jasmine.SpyObj<Router>;
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
    // Create spy objects
    mockRegionService = jasmine.createSpyObj('RegionService', [
      'getRegion',
      'listRegions',
      'listFeedsForRegion',
      'sortWithCanadianPriority',
      'clearCache',
    ]);
    mockImportService = jasmine.createSpyObj('ImportService', [
      'getActiveImports',
      'startPollingActiveImports',
      'stopPollingActiveImports',
      'getActiveImportsObservable',
      'refreshActiveImports',
      'getActiveRegionImport',
      'monitorRegionImportProgress',
      'startImport',
      'importAllFeedsForRegion',
      'cancelImport',
    ]);
    mockSchedulerService = jasmine.createSpyObj('SchedulerService', [
      'enableFeedAutoUpdate',
      'disableFeedAutoUpdate',
    ]);
    mockSnackBar = jasmine.createSpyObj('MatSnackBar', ['open']);
    mockAgencyService = jasmine.createSpyObj('AgencyService', ['listAgencies']);
    mockMetricsService = jasmine.createSpyObj('FeedsMetricsService', [
      'setSelectedRegion',
      'setDiscoverFeedCount',
    ]);
    mockEventsService = jasmine.createSpyObj('FeedsEventsService', ['']);
    mockRouter = jasmine.createSpyObj('Router', ['navigate']);

    // Setup default return values
    mockRegionService.listRegions.and.returnValue(of([mockRegion]));
    mockRegionService.listFeedsForRegion.and.returnValue(of([]));
    mockRegionService.sortWithCanadianPriority.and.callFake(
      (regions) => regions,
    );
    mockImportService.getActiveImports.and.returnValue(of([]));
    mockImportService.getActiveImportsObservable.and.returnValue(
      new BehaviorSubject([]).asObservable(),
    );
    mockImportService.getActiveRegionImport.and.returnValue(of(null));
    mockImportService.monitorRegionImportProgress.and.returnValue(of(null as any));
    mockImportService.startImport.and.returnValue(of({} as any));
    mockImportService.importAllFeedsForRegion.and.returnValue(
      of({ totalFeeds: 0, startedCount: 0, failedCount: 0, skippedCount: 0 } as any),
    );
    mockImportService.cancelImport.and.returnValue(of({} as any));
    mockAgencyService.listAgencies.and.returnValue(
      of({ content: [], totalElements: 0, totalPages: 0 } as any),
    );
    mockSnackBar.open.and.returnValue({ onAction: () => of(null) } as any);

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
    it('should initialize with no selected region', (done) => {
      fixture.detectChanges();

      component.selectedRegion$.subscribe((region) => {
        expect(region).toBeNull();
        done();
      });
    });

    it('should subscribe to route parameter changes on init', () => {
      spyOn(component['route'].paramMap, 'pipe').and.returnValue(
        of(convertToParamMap({})),
      );

      component.ngOnInit();

      expect(component['route'].paramMap.pipe).toHaveBeenCalled();
    });

    it('should subscribe to query parameter changes on init', () => {
      spyOn(component['route'].queryParamMap, 'pipe').and.returnValue(
        of(convertToParamMap({})),
      );

      component.ngOnInit();

      expect(component['route'].queryParamMap.pipe).toHaveBeenCalled();
    });
  });

  describe('route parameter handling', () => {
    it('should load region when regionId param is present', () => {
      mockRegionService.getRegion.and.returnValue(of(mockRegionDetail));

      fixture.detectChanges();
      paramMapSubject.next(convertToParamMap({ regionId: 'r-test-toronto' }));

      expect(mockRegionService.getRegion).toHaveBeenCalledWith(
        'r-test-toronto',
      );
    });

    it('should update selectedRegion$ when region loads successfully', (done) => {
      mockRegionService.getRegion.and.returnValue(of(mockRegionDetail));

      fixture.detectChanges();
      paramMapSubject.next(convertToParamMap({ regionId: 'r-test-toronto' }));

      setTimeout(() => {
        component.selectedRegion$.subscribe((region) => {
          expect(region).toEqual(mockRegionDetail);
          done();
        });
      }, 100);
    });

    it('should clear selectedRegion$ when regionId param is removed', (done) => {
      mockRegionService.getRegion.and.returnValue(of(mockRegionDetail));

      fixture.detectChanges();
      paramMapSubject.next(convertToParamMap({ regionId: 'r-test-toronto' }));

      setTimeout(() => {
        paramMapSubject.next(convertToParamMap({}));

        setTimeout(() => {
          component.selectedRegion$.subscribe((region) => {
            expect(region).toBeNull();
            done();
          });
        }, 100);
      }, 100);
    });

    it('should handle region loading errors gracefully', (done) => {
      mockRegionService.getRegion.and.returnValue(
        throwError(() => new Error('Failed to load region')),
      );
      spyOn(console, 'error');

      fixture.detectChanges();
      paramMapSubject.next(convertToParamMap({ regionId: 'r-test-toronto' }));

      setTimeout(() => {
        expect(console.error).toHaveBeenCalledWith(
          'Failed to load region:',
          jasmine.any(Error),
        );
        component.selectedRegion$.subscribe((region) => {
          expect(region).toBeNull();
          done();
        });
      }, 100);
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
      mockRegionService.getRegion.and.returnValue(of(mockRegionDetail));

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
      const destroySpy = spyOn(component['destroy$'], 'next');
      const completeSpy = spyOn(component['destroy$'], 'complete');

      component.ngOnDestroy();

      expect(destroySpy).toHaveBeenCalled();
      expect(completeSpy).toHaveBeenCalled();
    });

    it('should unsubscribe from observables on destroy', () => {
      mockRegionService.getRegion.and.returnValue(of(mockRegionDetail));

      fixture.detectChanges();
      paramMapSubject.next(convertToParamMap({ regionId: 'r-test-toronto' }));

      const subscription = component['destroy$'].subscribe();
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
      mockRegionService.getRegion.and.returnValue(of(mockRegionDetail));

      fixture.detectChanges();
      paramMapSubject.next(convertToParamMap({ regionId: 'r-test-toronto' }));
      fixture.detectChanges();

      const masterPanel = fixture.debugElement.query(
        (el) => el.name === 'app-region-master-panel',
      );

      expect(masterPanel).toBeTruthy();
    });

    it('should pass selectedRegion to detail panel', () => {
      mockRegionService.getRegion.and.returnValue(of(mockRegionDetail));

      fixture.detectChanges();
      paramMapSubject.next(convertToParamMap({ regionId: 'r-test-toronto' }));
      fixture.detectChanges();

      const detailPanel = fixture.debugElement.query(
        (el) => el.name === 'app-region-detail-panel',
      );

      expect(detailPanel).toBeTruthy();
    });

    it('should apply has-selection class when region is selected', (done) => {
      mockRegionService.getRegion.and.returnValue(of(mockRegionDetail));

      fixture.detectChanges();
      paramMapSubject.next(convertToParamMap({ regionId: 'r-test-toronto' }));

      setTimeout(() => {
        fixture.detectChanges();
        const container =
          fixture.nativeElement.querySelector('.regions-container');
        expect(container.classList.contains('has-selection')).toBe(true);
        done();
      }, 100);
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
