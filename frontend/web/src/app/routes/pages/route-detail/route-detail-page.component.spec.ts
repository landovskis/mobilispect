import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute } from '@angular/router';
import { of } from 'rxjs';
import { RouteDetailPageComponent } from './route-detail-page.component';
import { RouteService } from '../../services/route.service';
import { CommonSectionService } from '../../services/common-section.service';
import { vi } from 'vitest';

describe('RouteDetailPageComponent', () => {
  let component: RouteDetailPageComponent;
  let fixture: ComponentFixture<RouteDetailPageComponent>;
  let mockRouteService: RouteService;
  let mockActivatedRoute: ActivatedRoute;
  let mockCommonSectionService: CommonSectionService;

  beforeEach(async () => {
    mockRouteService = {
      getRoute: vi.fn(),
      getVariants: vi.fn(),
      getFrequencies: vi.fn(),
      getCommonSections: vi.fn(),
    } as unknown as RouteService;
    mockCommonSectionService = {
      getCommonSectionsForRoute: vi.fn(),
      getCombinedFrequency: vi.fn(),
    } as unknown as CommonSectionService;

    mockActivatedRoute = {
      snapshot: {
        paramMap: {
          get: vi.fn().mockReturnValue('test-route-id'),
        },
      },
    } as unknown as ActivatedRoute;

    await TestBed.configureTestingModule({
      imports: [RouteDetailPageComponent],
      providers: [
        { provide: RouteService, useValue: mockRouteService },
        { provide: CommonSectionService, useValue: mockCommonSectionService },
        { provide: ActivatedRoute, useValue: mockActivatedRoute },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(RouteDetailPageComponent);
    component = fixture.componentInstance;
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  it('should load route data on init', () => {
    const mockRoute = {
      id: 'test-route-id',
      agencyId: 'test-agency',
      shortName: '5',
      longName: 'Test Route',
      routeType: 'BUS',
      active: true,
    };

    const mockVariants = [
      {
        id: 'variant-1',
        routeId: 'test-route-id',
        directionId: 0,
        headsign: 'Downtown',
        stopCount: 10,
        stopPattern: 'stop1|stop2|stop3',
        firstStopId: 'stop1',
        lastStopId: 'stop3',
      },
    ];

    const mockSections = [
      {
        id: 'section-1',
        stopPattern: 'stop1|stop2|stop3',
        stopCount: 3,
        firstStopId: 'stop1',
        lastStopId: 'stop3',
        variants: ['variant-1'],
      },
    ];

    vi.mocked(mockRouteService.getRoute).mockReturnValue(of(mockRoute));
    vi.mocked(mockRouteService.getVariants).mockReturnValue(of(mockVariants));
    vi.mocked(mockRouteService.getFrequencies).mockReturnValue(of([]));
    vi.mocked(mockRouteService.getCommonSections).mockReturnValue(of([]));
    vi.mocked(mockCommonSectionService.getCommonSectionsForRoute).mockReturnValue(of(mockSections));
    vi.mocked(mockCommonSectionService.getCombinedFrequency).mockReturnValue(
      of({
        commonSectionId: 'section-1',
        timePeriod: 'WEEKDAY_AM_PEAK',
        averageHeadwayMinutes: 10,
        tripCount: 12,
        isIrregular: false,
      })
    );

    fixture.detectChanges();

    expect(mockRouteService.getRoute).toHaveBeenCalledWith('test-route-id');
    expect(mockRouteService.getVariants).toHaveBeenCalledWith('test-route-id');
    expect(mockCommonSectionService.getCommonSectionsForRoute).toHaveBeenCalledWith(
      'test-route-id'
    );
  });

  it('should return route type label', () => {
    expect(component.getRouteTypeLabel('BUS')).toBe('Bus');
    expect(component.getRouteTypeLabel('SUBWAY')).toBe('Subway/Metro');
    expect(component.getRouteTypeLabel('UNKNOWN')).toBe('UNKNOWN');
  });
});
