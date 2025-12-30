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
      ]
    });
  });

  it('requests frequencies with optional date', () => {
    service.getFrequencies('variant-1', '2024-01-01').subscribe(frequencies => {
      expect(frequencies.length).toBe(1);
    });

    const reqWithDate = httpMock.expectOne(request => request.url === '/api/v1/routes/variants/variant-1/frequencies');
    expect(reqWithDate.request.params.get('date')).toBe('2024-01-01');
    reqWithDate.flush([
      {
        id: 'freq-1',
        variantId: 'variant-1',
        serviceDate: '2024-01-01',
        timePeriod: 'AM',
        averageHeadwayMinutes: 10,
        minHeadwayMinutes: 8,
        maxHeadwayMinutes: 12,
        tripCount: 30,
        isIrregular: false
      }
    ]);

    service.getFrequencies('variant-2').subscribe(frequencies => {
      expect(frequencies.length).toBe(0);
    });

    const reqWithoutDate = httpMock.expectOne('/api/v1/routes/variants/variant-2/frequencies');
    expect(reqWithoutDate.request.params.has('date')).toBeFalse();
    reqWithoutDate.flush([]);
  });

  it('loads hourly frequencies for routes and variants', () => {
    service.getRouteHourlyFrequencies('route-3', '2024-01-02').subscribe(items => {
      expect(items.length).toBe(1);
    });

    const routeReq = httpMock.expectOne(request =>
      request.url === '/api/v1/routes/route-3/hourly-frequencies' &&
      request.params.get('date') === '2024-01-02'
    );
    routeReq.flush([
      {
        routeId: 'route-3',
        serviceDate: '2024-01-02',
        hourOfDay: 9,
        averageHeadwayMinutes: 12,
        minHeadwayMinutes: 10,
        maxHeadwayMinutes: 15,
        tripCount: 12,
        variantCount: 2,
        isIrregular: false
      }
    ]);

    service.getVariantHourlyFrequencies('variant-2', '2024-01-03').subscribe(items => {
      expect(items.length).toBe(1);
    });

    const variantReq = httpMock.expectOne(request =>
      request.url === '/api/v1/routes/variants/variant-2/hourly-frequencies' &&
      request.params.get('date') === '2024-01-03'
    );
    variantReq.flush([
      {
        variantId: 'variant-2',
        serviceDate: '2024-01-03',
        hourOfDay: 15,
        averageHeadwayMinutes: 20,
        minHeadwayMinutes: 15,
        maxHeadwayMinutes: 25,
        tripCount: 6,
        isIrregular: true
      }
    ]);
  });
});
