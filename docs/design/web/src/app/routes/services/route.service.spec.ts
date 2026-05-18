import { TestBed } from '@angular/core/testing';
import { HttpClientTestingModule, HttpTestingController } from '@angular/common/http/testing';
import { RouteService } from './route.service';

describe('RouteService', () => {
  let service: RouteService;
  let httpMock: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [HttpClientTestingModule],
      providers: [RouteService],
    });

    service = TestBed.inject(RouteService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpMock.verify();
  });

  it('fetches a route by id', () => {
    service.getRoute('route-1').subscribe((route) => {
      expect(route.id).toBe('route-1');
      expect(route.longName).toBe('Main Line');
    });

    const req = httpMock.expectOne('/api/v1/routes/route-1');
    expect(req.request.method).toBe('GET');
    req.flush({
      id: 'route-1',
      agencyId: 'agency-1',
      longName: 'Main Line',
      routeType: 'BUS',
      active: true,
    });
  });

  it('fetches variants for a route', () => {
    service.getVariants('route-1').subscribe((variants) => {
      expect(variants.length).toBe(1);
      expect(variants[0].id).toBe('variant-1');
    });

    const req = httpMock.expectOne('/api/v1/routes/route-1/variants');
    expect(req.request.method).toBe('GET');
    req.flush([
      {
        id: 'variant-1',
        routeId: 'route-1',
        stopCount: 10,
        stopPattern: 'a-b-c',
        firstStopId: 'stop-a',
        lastStopId: 'stop-c',
      },
    ]);
  });

  it('fetches frequencies for a variant without date', () => {
    service.getFrequencies('variant-1').subscribe((frequencies) => {
      expect(frequencies.length).toBe(1);
      expect(frequencies[0].variantId).toBe('variant-1');
    });

    const req = httpMock.expectOne('/api/v1/routes/variants/variant-1/frequencies');
    expect(req.request.method).toBe('GET');
    expect(req.request.params.has('date')).toBe(false);
    req.flush([
      {
        id: 'freq-1',
        variantId: 'variant-1',
        serviceDate: '2024-01-15',
        timePeriod: 'AM_PEAK',
        tripCount: 5,
        isIrregular: false,
      },
    ]);
  });

  it('fetches frequencies for a variant with date', () => {
    service.getFrequencies('variant-1', '2024-01-15').subscribe();

    const req = httpMock.expectOne('/api/v1/routes/variants/variant-1/frequencies?date=2024-01-15');
    expect(req.request.method).toBe('GET');
    expect(req.request.params.get('date')).toBe('2024-01-15');
    req.flush([]);
  });

  it('fetches hourly frequencies for a route', () => {
    service.getRouteHourlyFrequencies('route-1', '2024-01-15').subscribe((data) => {
      expect(data.length).toBe(1);
      expect(data[0].routeId).toBe('route-1');
    });

    const req = httpMock.expectOne('/api/v1/routes/route-1/hourly-frequencies?date=2024-01-15');
    expect(req.request.method).toBe('GET');
    req.flush([
      {
        routeId: 'route-1',
        serviceDate: '2024-01-15',
        hourOfDay: 8,
        tripCount: 3,
        variantCount: 1,
        isIrregular: false,
      },
    ]);
  });

  it('fetches hourly frequencies for a variant', () => {
    service.getVariantHourlyFrequencies('variant-1', '2024-01-15').subscribe((data) => {
      expect(data.length).toBe(1);
      expect(data[0].variantId).toBe('variant-1');
    });

    const req = httpMock.expectOne(
      '/api/v1/routes/variants/variant-1/hourly-frequencies?date=2024-01-15'
    );
    expect(req.request.method).toBe('GET');
    req.flush([
      {
        variantId: 'variant-1',
        serviceDate: '2024-01-15',
        hourOfDay: 9,
        tripCount: 4,
        isIrregular: false,
      },
    ]);
  });

  it('fetches the complete schedule for a variant', () => {
    service.getCompleteSchedule('variant-1').subscribe((times) => {
      expect(times).toEqual(['06:00', '06:15', '06:30']);
    });

    const req = httpMock.expectOne('/api/v1/routes/variants/variant-1/schedule');
    expect(req.request.method).toBe('GET');
    req.flush(['06:00', '06:15', '06:30']);
  });

  it('fetches common sections for a route', () => {
    service.getCommonSections('route-1').subscribe((sections) => {
      expect(sections.length).toBe(1);
      expect(sections[0].routeId).toBe('route-1');
    });

    const req = httpMock.expectOne('/api/v1/routes/route-1/common-sections');
    expect(req.request.method).toBe('GET');
    req.flush([
      {
        id: 'section-1',
        routeId: 'route-1',
        stopPattern: 'a-b-c',
        stopNames: ['Stop A', 'Stop B', 'Stop C'],
        stopCount: 3,
        firstStopId: 'stop-a',
        lastStopId: 'stop-c',
        variantCount: 2,
      },
    ]);
  });
});
