import { TestBed } from '@angular/core/testing';
import {
  HttpTestingController,
  provideHttpClientTesting,
} from '@angular/common/http/testing';
import { provideHttpClient } from '@angular/common/http';
import { CorridorService, CorridorDto } from './corridor.service';
import { environment } from '../../../environments/environment';

describe('CorridorService', () => {
  let service: CorridorService;
  let httpMock: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    service = TestBed.inject(CorridorService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpMock.verify();
  });

  it('should be created', () => {
    expect(service).toBeTruthy();
  });

  it('should fetch corridors for a region', () => {
    const regionId = 'r-abc';
    const mockCorridors: CorridorDto[] = [
      {
        id: '1',
        stopPattern: 's1|s2|s3',
        stopCount: 3,
        firstStopId: 's1',
        lastStopId: 's3',
        routes: [
          { routeId: 'r-1', shortName: '10', longName: 'Route 10' },
          { routeId: 'r-2', shortName: '20', longName: 'Route 20' },
        ],
      },
    ];

    service.getCorridorsForRegion(regionId).subscribe((corridors) => {
      expect(corridors).toEqual(mockCorridors);
      expect(corridors.length).toBe(1);
    });

    const req = httpMock.expectOne(
      `${environment.apiUrl}/v1/regions/${regionId}/corridors`,
    );
    expect(req.request.method).toBe('GET');
    req.flush(mockCorridors);
  });

  it('should return empty array when no corridors', () => {
    const regionId = 'r-empty';

    service.getCorridorsForRegion(regionId).subscribe((corridors) => {
      expect(corridors).toEqual([]);
    });

    const req = httpMock.expectOne(
      `${environment.apiUrl}/v1/regions/${regionId}/corridors`,
    );
    req.flush([]);
  });
});
