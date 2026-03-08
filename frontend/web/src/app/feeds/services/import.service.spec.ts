import { TestBed, fakeAsync, tick } from '@angular/core/testing';
import { HttpClientTestingModule, HttpTestingController } from '@angular/common/http/testing';
import { firstValueFrom, of, EMPTY } from 'rxjs';
import { ImportService } from './import.service';
import { environment } from '../../../environments/environment';
import { WebSocketService } from './websocket.service';
import {
  FeedImport,
  FeedImportDetail,
  FeedImportSummary,
  ImportProgress,
  ImportStatus,
  TriggerType,
  RegionImportStatus,
  RegionImportStatusResponse,
} from '../models/import.models';
import { vi } from 'vitest';

describe('ImportService', () => {
  let service: ImportService;
  let httpMock: HttpTestingController;

  let mockWebSocketService: WebSocketService;
  let internals: ImportServiceInternals;

  const baseImport: FeedImport = {
    id: 'imp-1',
    feedOnestopId: 'f-1',
    administratorId: null,
    administratorUsername: null,
    triggerType: TriggerType.MANUAL,
    status: ImportStatus.PENDING,
    versionSha1: null,
    startedAt: '2024-01-01T00:00:00Z',
    completedAt: null,
    fileSizeBytes: null,
    errorMessage: null,
    createdAt: '2024-01-01T00:00:00Z',
    updatedAt: '2024-01-01T00:00:00Z',
  };

  const baseImportSummary: FeedImportSummary = {
    id: 'imp-1',
    feedOnestopId: 'f-1',
    feedName: 'Feed 1',
    regionOnestopId: 'r-1',
    regionName: 'Region 1',
    status: ImportStatus.PENDING,
    triggerType: TriggerType.MANUAL,
    startedAt: '2024-01-01T00:00:00Z',
    completedAt: null,
    fileSizeBytes: null,
    errorMessage: null,
    progress: null,
  };

  const baseImportDetail: FeedImportDetail = {
    ...baseImport,
    feedName: 'Feed 1',
    regionName: 'Region 1',
    progress: null,
  };

  const baseRegionImport: RegionImportStatusResponse = {
    regionImportId: 'reg-1',
    regionOnestopId: 'r-1',
    status: RegionImportStatus.RUNNING,
    totalFeeds: 10,
    startedCount: 5,
    completedCount: 2,
    failedCount: 1,
    skippedCount: 0,
    startedAt: '2024-01-01T00:00:00Z',
    completedAt: null,
    errorMessage: null,
    createdAt: '2024-01-01T00:00:00Z',
    updatedAt: '2024-01-01T00:00:00Z',
  };

  beforeEach(() => {
    mockWebSocketService = {
      connect: vi.fn(),
      startHeartbeat: vi.fn(),
      disconnect: vi.fn(),
      subscribeToImportProgress: vi.fn(),
      subscribeToImportStatus: vi.fn(),
    } as unknown as WebSocketService;
    vi.mocked(mockWebSocketService.subscribeToImportProgress).mockReturnValue(EMPTY);
    vi.mocked(mockWebSocketService.subscribeToImportStatus).mockReturnValue(EMPTY);

    TestBed.configureTestingModule({
      imports: [HttpClientTestingModule],
      providers: [ImportService, { provide: WebSocketService, useValue: mockWebSocketService }],
    });

    service = TestBed.inject(ImportService);
    httpMock = TestBed.inject(HttpTestingController);
    internals = service as unknown as ImportServiceInternals;
  });

  afterEach(() => {
    httpMock.verify();
  });

  it('starts imports and triggers polling', () => {
    vi.spyOn(service, 'startPollingActiveImports').mockImplementation(() => {});

    service.startImport('f-1').subscribe((result) => {
      expect(result.id).toBe('imp-1');
    });

    const req = httpMock.expectOne(`${environment.apiUrl}/feeds/f-1/import`);
    expect(req.request.method).toBe('POST');
    req.flush({ ...baseImport });

    expect(service.startPollingActiveImports).toHaveBeenCalled();
  });

  it('wraps backend errors with user-friendly message', async () => {
    const errorPromise = firstValueFrom(service.startImport('f-1')).catch((e) => e);

    const req = httpMock.expectOne(`${environment.apiUrl}/feeds/f-1/import`);
    req.flush({ message: 'Forbidden' }, { status: 403, statusText: 'Forbidden' });

    const error = await errorPromise;
    expect(error.isBackendError).toBe(true);
    expect(error.message).toContain('Authentication required');
  });

  it('formats backend error messages for common statuses', () => {
    const getErrorMessage = internals.getErrorMessage.bind(service);

    expect(getErrorMessage({ status: 0 })).toContain('Cannot connect');
    expect(getErrorMessage({ status: 404 })).toContain('Feed not found');
    expect(getErrorMessage({ status: 503 })).toContain('temporarily unavailable');
    expect(getErrorMessage({ status: 500, statusText: 'Oops' })).toContain('Oops');
    expect(getErrorMessage({ status: 400, error: { message: 'Bad request' } })).toBe('Bad request');
  });

  it('maps import history and query params', () => {
    service
      .getFeedImportHistory('f-1', {
        page: 1,
        size: 10,
        status: ImportStatus.FAILED,
      })
      .subscribe((result) => {
        expect(result.totalElements).toBe(1);
        expect(result.imports[0].id).toBe('imp-1');
      });

    const req = httpMock.expectOne(
      (request) => request.url === `${environment.apiUrl}/feeds/f-1/imports`
    );
    expect(req.request.params.get('page')).toBe('1');
    expect(req.request.params.get('size')).toBe('10');
    expect(req.request.params.get('status')).toBe(ImportStatus.FAILED);
    req.flush({
      imports: [{ ...baseImportDetail, status: ImportStatus.FAILED }],
      page: {
        page: 1,
        size: 10,
        totalElements: 1,
        totalPages: 1,
        hasNext: false,
        hasPrevious: false,
      },
    });
  });

  it('requests import history with default params', () => {
    service.getFeedImportHistory('f-1').subscribe((result) => {
      expect(result.totalElements).toBe(0);
    });

    const req = httpMock.expectOne(
      (request) => request.url === `${environment.apiUrl}/feeds/f-1/imports`
    );
    expect(req.request.params.keys().length).toBe(0);
    req.flush({
      imports: [],
      page: {
        page: 0,
        size: 20,
        totalElements: 0,
        totalPages: 0,
        hasNext: false,
        hasPrevious: false,
      },
    });
  });

  it('refreshes active imports on cancel', () => {
    vi.spyOn(service, 'refreshActiveImports').mockImplementation(() => {});

    service.cancelImport('imp-1').subscribe((result) => {
      expect(result.id).toBe('imp-1');
    });

    const req = httpMock.expectOne(`${environment.apiUrl}/feeds/imports/imp-1`);
    expect(req.request.method).toBe('DELETE');
    req.flush({ ...baseImport, status: ImportStatus.CANCELLED });

    expect(service.refreshActiveImports).toHaveBeenCalled();
  });

  it('updates active imports cache', () => {
    service.getActiveImports().subscribe((imports) => {
      expect(imports.length).toBe(1);
    });

    const req = httpMock.expectOne(`${environment.apiUrl}/feeds/imports/active`);
    req.flush({ imports: [{ ...baseImportSummary }], total: 1 });

    service.getActiveImportsObservable().subscribe((imports) => {
      expect(imports.length).toBe(1);
    });
  });

  it('fetches active region import status', () => {
    service.getActiveRegionImport('r-1').subscribe((status) => {
      expect(status?.regionImportId).toBe('reg-1');
    });

    const req = httpMock.expectOne(`${environment.apiUrl}/feeds/regions/r-1/imports/active`);
    expect(req.request.method).toBe('GET');
    req.flush({ ...baseRegionImport });
  });

  it('polls region import status until terminal', fakeAsync(() => {
    const running = { ...baseRegionImport, status: RegionImportStatus.RUNNING };
    const completed = {
      ...baseRegionImport,
      status: RegionImportStatus.COMPLETED,
    };
    let callCount = 0;
    vi.spyOn(service, 'getRegionImportStatus').mockImplementation(() => {
      callCount++;
      return callCount === 1 ? of(running) : of(completed);
    });
    internals.pollingInterval = 10;

    const results: RegionImportStatusResponse[] = [];
    service.monitorRegionImportProgress('reg-1').subscribe((status) => results.push(status));

    tick(0);
    tick(internals.pollingInterval);

    expect(results.length).toBeGreaterThan(0);
    expect(results[results.length - 1].status).toBe(RegionImportStatus.COMPLETED);
  }));

  it('finds active import for a feed', () => {
    vi.spyOn(service, 'getActiveImports').mockReturnValue(
      of([
        { ...baseImportSummary, feedOnestopId: 'f-1' },
        { ...baseImportSummary, id: 'imp-2', feedOnestopId: 'f-2' },
      ])
    );

    service.getActiveImportForFeed('f-2').subscribe((result) => {
      expect(result?.id).toBe('imp-2');
    });
  });

  it('returns null when no active import matches', () => {
    vi.spyOn(service, 'getActiveImports').mockReturnValue(
      of([{ ...baseImportSummary, feedOnestopId: 'f-1' }])
    );

    service.getActiveImportForFeed('f-2').subscribe((result) => {
      expect(result).toBeNull();
    });
  });

  it('retries imports based on existing import data', () => {
    vi.spyOn(service, 'getImport').mockReturnValue(
      of({
        ...baseImportDetail,
        feedOnestopId: 'f-9',
        status: ImportStatus.FAILED,
      })
    );
    vi.spyOn(service, 'startImport').mockReturnValue(
      of({
        ...baseImport,
        id: 'imp-new',
        feedOnestopId: 'f-9',
      })
    );

    service.retryImport('imp-1').subscribe((result) => {
      expect(result.id).toBe('imp-new');
    });
  });

  it('filters recent and failed imports', () => {
    vi.useFakeTimers();
    vi.setSystemTime(new Date('2024-06-02T12:00:00Z'));

    const recent = {
      ...baseImportDetail,
      id: 'recent',
      createdAt: '2024-06-02T11:00:00Z',
      status: ImportStatus.COMPLETED,
    };
    const old = {
      ...baseImportDetail,
      id: 'old',
      createdAt: '2024-05-30T11:00:00Z',
      status: ImportStatus.COMPLETED,
    };
    const failed = {
      ...baseImportDetail,
      id: 'failed',
      createdAt: '2024-06-02T10:00:00Z',
      status: ImportStatus.FAILED,
    };

    vi.spyOn(service, 'getAllImportHistory').mockImplementation((options) => {
      if (options?.status === ImportStatus.FAILED) {
        return of({ imports: [failed], totalElements: 1, totalPages: 1 });
      }

      return of({
        imports: [recent, old, failed],
        totalElements: 3,
        totalPages: 1,
      });
    });

    service.getRecentImports().subscribe((imports) => {
      expect(imports.length).toBe(2);
    });

    service.getFailedImports().subscribe((imports) => {
      expect(imports.length).toBe(1);
      expect(imports[0].id).toBe('failed');
    });

    vi.useRealTimers();
  });

  it('maps import statistics from active imports', () => {
    vi.spyOn(service, 'getActiveImports').mockReturnValue(
      of([{ ...baseImportSummary }, { ...baseImportSummary, id: 'imp-2' }])
    );

    service.getImportStatistics().subscribe((stats) => {
      expect(stats.activeImports).toBe(2);
    });
  });

  it('polls active imports on interval', fakeAsync(() => {
    vi.spyOn(service, 'getActiveImports').mockReturnValue(of([]));
    internals.pollingInterval = 10;

    service.startPollingActiveImports();
    tick(0);
    tick(10);

    expect(service.getActiveImports).toHaveBeenCalled();
    service.stopPollingActiveImports();
  }));

  it('skips polling when already active', () => {
    internals.isPolling = true;
    vi.spyOn(service, 'getActiveImports').mockImplementation(() => of([]));

    service.startPollingActiveImports();

    expect(service.getActiveImports).not.toHaveBeenCalled();
  });

  it('refreshes active imports on demand', () => {
    vi.spyOn(service, 'getActiveImports').mockReturnValue(of([]));

    service.refreshActiveImports();

    expect(service.getActiveImports).toHaveBeenCalled();
  });

  it('checks whether an import is running for a feed', () => {
    vi.spyOn(service, 'getActiveImports').mockReturnValue(
      of([{ ...baseImportSummary, feedOnestopId: 'f-1' }])
    );

    service.isImportRunningForFeed('f-1').subscribe((isRunning) => {
      expect(isRunning).toBe(true);
    });
  });

  it('requests all import history with filters', () => {
    service
      .getAllImportHistory({
        page: 2,
        size: 5,
        status: ImportStatus.RUNNING,
        triggerType: TriggerType.AUTOMATIC,
      })
      .subscribe((result) => {
        expect(result.totalElements).toBe(1);
      });

    const req = httpMock.expectOne(
      (request) => request.url === `${environment.apiUrl}/feeds/imports`
    );
    expect(req.request.params.get('page')).toBe('2');
    expect(req.request.params.get('size')).toBe('5');
    expect(req.request.params.get('status')).toBe(ImportStatus.RUNNING);
    expect(req.request.params.get('triggerType')).toBe(TriggerType.AUTOMATIC);
    req.flush({
      imports: [{ ...baseImport, status: ImportStatus.RUNNING }],
      page: {
        page: 2,
        size: 5,
        totalElements: 1,
        totalPages: 1,
        hasNext: false,
        hasPrevious: false,
      },
    });
  });

  it('merges progress updates from polling and websocket', fakeAsync(() => {
    vi.spyOn(service, 'getImportProgress').mockReturnValue(
      of({
        progressPercentage: 10,
        totalSteps: 100,
        currentStep: 'Init',
        estimatedTimeRemainingSeconds: null,
      })
    );

    vi.mocked(mockWebSocketService.subscribeToImportProgress).mockReturnValue(
      of({
        progress: {
          importId: 'imp-1',
          feedOnestopId: 'f-1',
          progressPercentage: 20,
          totalSteps: 100,
          currentStep: 'Step',
          currentStepNumber: 2,
          estimatedTimeRemainingSeconds: 5,
          startedAt: '2024-01-01T00:00:00Z',
          lastUpdatedAt: '2024-01-01T00:00:05Z',
        },
      })
    );

    const results: ImportProgress[] = [];
    service.monitorImportProgress('imp-1').subscribe((value) => results.push(value));

    tick(0);

    expect(results.length).toBeGreaterThan(0);
    expect(results[0].progressPercentage).toBeDefined();
  }));

  it('merges status updates from polling and websocket', fakeAsync(() => {
    const detail = { ...baseImportDetail, status: ImportStatus.RUNNING };
    vi.spyOn(service, 'getImport').mockReturnValue(of(detail));
    vi.mocked(mockWebSocketService.subscribeToImportStatus).mockReturnValue(
      of({
        type: 'IMPORT_STATUS',
        data: {
          importId: 'imp-1',
          status: ImportStatus.RUNNING,
        },
        timestamp: '2024-01-01T00:00:00Z',
      })
    );

    const results: FeedImportDetail[] = [];
    service.monitorImportStatus('imp-1').subscribe((value) => results.push(value));

    tick(0);

    expect(results.length).toBeGreaterThan(0);
    expect(results[0].status).toBe(ImportStatus.RUNNING);
  }));
});

type ImportServiceInternals = {
  getErrorMessage: (error: unknown) => string;
  pollingInterval: number;
  isPolling: boolean;
};
