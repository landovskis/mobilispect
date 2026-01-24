import { TestBed } from '@angular/core/testing';
import {
  HttpClientTestingModule,
  HttpTestingController,
} from '@angular/common/http/testing';
import { AgencyService } from './agency.service';
import { environment } from '../../../environments/environment';
import { RouteType } from '../../routes/models/route-type.model';

describe('AgencyService', () => {
  let service: AgencyService;
  let httpMock: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [HttpClientTestingModule],
      providers: [AgencyService],
    });

    service = TestBed.inject(AgencyService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpMock.verify();
  });

  it('lists agencies with region scope when provided', () => {
    service.listAgencies(2, 50, 'r-1').subscribe((response) => {
      expect(response.totalElements).toBe(1);
    });

    const req = httpMock.expectOne(
      (request) => request.url === `${environment.apiUrl}/regions/r-1/agencies`,
    );
    expect(req.request.params.get('page')).toBe('2');
    expect(req.request.params.get('size')).toBe('50');

    req.flush({
      content: [
        {
          id: 'a-1',
          name: 'Metro',
          feedOnestopId: 'f-metro',
          regionIds: ['r-1'],
          routeCount: 10,
          activeRouteCount: 8,
          routesByType: { bus: 10 },
        },
      ],
      totalElements: 1,
      totalPages: 1,
    });
  });

  it('lists agencies without region scope by default', () => {
    service.listAgencies().subscribe((response) => {
      expect(response.totalPages).toBe(2);
    });

    const req = httpMock.expectOne(
      (request) => request.url === `${environment.apiUrl}/agencies`,
    );
    expect(req.request.params.get('page')).toBe('0');
    expect(req.request.params.get('size')).toBe('20');

    req.flush({
      content: [],
      totalElements: 20,
      totalPages: 2,
    });
  });

  it('gets a single agency summary', () => {
    service.getAgency('a-2').subscribe((agency) => {
      expect(agency.id).toBe('a-2');
    });

    const req = httpMock.expectOne(`${environment.apiUrl}/agencies/a-2`);
    expect(req.request.method).toBe('GET');

    req.flush({
      id: 'a-2',
      name: 'Metro',
      routeCount: 12,
      averageHeadwayMinutes: 15,
      minHeadwayMinutes: 10,
      maxHeadwayMinutes: 30,
    });
  });

  it('lists routes for an agency with paging', () => {
    service.listRoutesByAgency('a-3', 1, 25).subscribe((response) => {
      expect(response.content.length).toBe(1);
      expect(response.number).toBe(1);
    });

    const req = httpMock.expectOne(
      (request) => request.url === `${environment.apiUrl}/agencies/a-3/routes`,
    );
    expect(req.request.params.get('page')).toBe('1');
    expect(req.request.params.get('size')).toBe('25');

    req.flush({
      content: [
        {
          id: 'r-1',
          agencyId: 'a-3',
          shortName: '10',
          longName: 'Downtown',
          routeType: RouteType.BUS,
          active: true,
        },
      ],
      totalElements: 1,
      totalPages: 1,
      number: 1,
      size: 25,
    });
  });
});
