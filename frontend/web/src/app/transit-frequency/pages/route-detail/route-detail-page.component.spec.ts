import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute } from '@angular/router';
import { of } from 'rxjs';
import { RouteDetailPageComponent } from './route-detail-page.component';
import { FrequencyService } from '../../services/frequency.service';

describe('RouteDetailPageComponent', () => {
  let component: RouteDetailPageComponent;
  let fixture: ComponentFixture<RouteDetailPageComponent>;
  let mockFrequencyService: jasmine.SpyObj<FrequencyService>;
  let mockActivatedRoute: any;

  beforeEach(async () => {
    mockFrequencyService = jasmine.createSpyObj('FrequencyService', [
      'getRoute',
      'getVariants',
      'getRouteHourlyFrequencies'
    ]);

    mockActivatedRoute = {
      snapshot: {
        paramMap: {
          get: jasmine.createSpy('get').and.returnValue('test-route-id')
        }
      }
    };

    await TestBed.configureTestingModule({
      imports: [RouteDetailPageComponent],
      providers: [
        { provide: FrequencyService, useValue: mockFrequencyService },
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

    const mockHourlyFrequencies = [
      {
        routeId: 'test-route-id',
        serviceDate: '2025-01-15',
        hourOfDay: 8,
        averageHeadwayMinutes: 15,
        minHeadwayMinutes: 12,
        maxHeadwayMinutes: 18,
        tripCount: 4,
        variantCount: 1,
        isIrregular: false
      }
    ];

    mockFrequencyService.getRoute.and.returnValue(of(mockRoute));
    mockFrequencyService.getVariants.and.returnValue(of(mockVariants));
    mockFrequencyService.getRouteHourlyFrequencies.and.returnValue(of(mockHourlyFrequencies));

    fixture.detectChanges();

    expect(mockFrequencyService.getRoute).toHaveBeenCalledWith('test-route-id');
    expect(mockFrequencyService.getVariants).toHaveBeenCalledWith('test-route-id');
    expect(mockFrequencyService.getRouteHourlyFrequencies).toHaveBeenCalledWith(
      'test-route-id',
      jasmine.any(String)
    );
  });

  it('should format hour correctly', () => {
    expect(component.formatHour(0)).toBe('00:00-01:00');
    expect(component.formatHour(8)).toBe('08:00-09:00');
    expect(component.formatHour(23)).toBe('23:00-00:00');
  });

  it('should return route type label', () => {
    expect(component.getRouteTypeLabel('BUS')).toBe('Bus');
    expect(component.getRouteTypeLabel('SUBWAY')).toBe('Subway/Metro');
    expect(component.getRouteTypeLabel('UNKNOWN')).toBe('UNKNOWN');
  });
});
