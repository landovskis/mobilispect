import { TestBed } from '@angular/core/testing';
import {
  HttpClientTestingModule,
  HttpTestingController,
} from '@angular/common/http/testing';
import { CommonSectionService } from './common-section.service';

describe('CommonSectionService', () => {
  let service: CommonSectionService;
  let httpMock: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [HttpClientTestingModule],
      providers: [CommonSectionService],
    });

    service = TestBed.inject(CommonSectionService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpMock.verify();
  });

  it('loads common sections for a route', () => {
    service.getCommonSectionsForRoute('route-1').subscribe((sections) => {
      expect(sections.length).toBe(1);
    });

    const req = httpMock.expectOne('/api/v1/common-sections/routes/route-1');
    expect(req.request.method).toBe('GET');
    req.flush([
      {
        id: 'section-1',
        stopPattern: 'A-B-C',
        stopCount: 3,
        firstStopId: 'stop-1',
        lastStopId: 'stop-3',
        variants: ['variant-1'],
      },
    ]);
  });

  it('loads combined frequency for a section', () => {
    service.getCombinedFrequency('section-2', 'PM').subscribe((frequency) => {
      expect(frequency?.commonSectionId).toBe('section-2');
    });

    const req = httpMock.expectOne(
      (request) =>
        request.url === '/api/v1/common-sections/section-2/frequency' &&
        request.params.get('timePeriod') === 'PM',
    );
    req.flush({
      commonSectionId: 'section-2',
      timePeriod: 'PM',
      averageHeadwayMinutes: 10,
      tripCount: 12,
      isIrregular: false,
      contributions: [
        {
          routeId: 'route-1',
          averageHeadwayMinutes: 12,
          tripCount: 6,
          isIrregular: false,
        },
      ],
    });
  });
});
