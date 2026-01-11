import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute } from '@angular/router';
import { of } from 'rxjs';
import { RouteDetailPageComponent } from './route-detail-page.component';
import { FrequencyService } from '../../services/frequency.service';
import { CommonSectionService } from '../../services/common-section.service';

describe('RouteDetailPageComponent', () => {
  let component: RouteDetailPageComponent;
  let fixture: ComponentFixture<RouteDetailPageComponent>;
  let mockFrequencyService: jasmine.SpyObj<FrequencyService>;
  let mockActivatedRoute: ActivatedRoute;
  let mockCommonSectionService: jasmine.SpyObj<CommonSectionService>;

  beforeEach(async () => {
    mockFrequencyService = jasmine.createSpyObj('FrequencyService', [
      'getRoute',
      'getVariants',
      'getFrequencies'
    ]);
    mockCommonSectionService = jasmine.createSpyObj('CommonSectionService', [
      'getCommonSectionsForRoute',
      'getCombinedFrequency'
    ]);

    mockActivatedRoute = {
      snapshot: {
        paramMap: {
          get: jasmine.createSpy('get').and.returnValue('test-route-id')
        }
      }
    } as ActivatedRoute;

    await TestBed.configureTestingModule({
      imports: [RouteDetailPageComponent],
      providers: [
        { provide: FrequencyService, useValue: mockFrequencyService },
        { provide: CommonSectionService, useValue: mockCommonSectionService },
        { provide: ActivatedRoute, useValue: mockActivatedRoute }
      ]
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
      active: true
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
        lastStopId: 'stop3'
      }
    ];

    const mockSections = [
      {
        id: 'section-1',
        stopPattern: 'stop1|stop2|stop3',
        stopCount: 3,
        firstStopId: 'stop1',
        lastStopId: 'stop3',
        variants: ['variant-1']
      }
    ];

    mockFrequencyService.getRoute.and.returnValue(of(mockRoute));
    mockFrequencyService.getVariants.and.returnValue(of(mockVariants));
    mockCommonSectionService.getCommonSectionsForRoute.and.returnValue(of(mockSections));
    mockCommonSectionService.getCombinedFrequency.and.returnValue(of({
      commonSectionId: 'section-1',
      timePeriod: 'WEEKDAY_AM_PEAK',
      averageHeadwayMinutes: 10,
      tripCount: 12,
      isIrregular: false
    }));

    fixture.detectChanges();

    expect(mockFrequencyService.getRoute).toHaveBeenCalledWith('test-route-id');
    expect(mockFrequencyService.getVariants).toHaveBeenCalledWith('test-route-id');
    expect(mockCommonSectionService.getCommonSectionsForRoute).toHaveBeenCalledWith('test-route-id');
  });

  it('should return route type label', () => {
    expect(component.getRouteTypeLabel('BUS')).toBe('Bus');
    expect(component.getRouteTypeLabel('SUBWAY')).toBe('Subway/Metro');
    expect(component.getRouteTypeLabel('UNKNOWN')).toBe('UNKNOWN');
  });
});
