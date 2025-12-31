import { TestBed } from '@angular/core/testing';
import { HttpClientTestingModule, HttpTestingController } from '@angular/common/http/testing';
import { FrequencyService } from './frequency.service';

describe('FrequencyService', () => {
  let service: FrequencyService;
  let httpMock: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [HttpClientTestingModule],
      providers: [FrequencyService]
    });

    service = TestBed.inject(FrequencyService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpMock.verify();
  });

  it('loads route details', () => {
    service.getRoute('route-1').subscribe(route => {
      expect(route.id).toBe('route-1');
      expect(route.variants.length).toBe(1);
      expect(route.hourlyStats.length).toBe(1);
    });

    const req = httpMock.expectOne('/api/v1/routes/route-1');
    expect(req.request.method).toBe('GET');
    req.flush({
      id: 'route-1',
      agencyId: 'agency-1',
      shortName: '10',
      longName: 'Main St',
      routeType: 'BUS',
      active: true,
      variants: [
        {
          id: 'variant-1',
          routeId: 'route-1',
          directionId: 0,
          headsign: 'North',
          stopCount: 12,
          stopPattern: 'A-B-C',
          firstStopId: 'stop-1',
          lastStopId: 'stop-12'
        }
      ],
      hourlyStats: [
        {
          serviceDate: '2024-01-01',
          directionId: 0,
          dayType: 'WEEKDAY',
          hourOfDay: 9,
          tripCount: 12,
          averageSpeedKph: 24.5
        }
      ]
    });
  });
});
