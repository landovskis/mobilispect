import { TestBed } from '@angular/core/testing';
import {
  HttpClientTestingModule,
  HttpTestingController,
} from '@angular/common/http/testing';
import { RegionService } from './region.service';

describe('RegionService', () => {
  let service: RegionService;
  let httpMock: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [HttpClientTestingModule],
      providers: [RegionService],
    });

    service = TestBed.inject(RegionService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpMock.verify();
  });

  it('lists regions for frequency analysis', () => {
    service.listRegions().subscribe((regions) => {
      expect(regions.length).toBe(1);
      expect(regions[0].id).toBe('r-1');
    });

    const req = httpMock.expectOne('/api/v1/frequency/regions');
    expect(req.request.method).toBe('GET');

    req.flush([
      {
        id: 'r-1',
        name: 'Test Region',
        adm0Name: 'Canada',
        adm1Name: 'Ontario',
        agencyCount: 4,
      },
    ]);
  });
});
