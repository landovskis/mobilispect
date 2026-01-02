import { TestBed, fakeAsync, tick } from '@angular/core/testing';
import { HttpClientTestingModule, HttpTestingController } from '@angular/common/http/testing';
import { of, EMPTY } from 'rxjs';
import { ImportService } from './import.service';
import { environment } from '../../../environments/environment';
import { WebSocketService } from './websocket.service';
import { FeedImport, FeedImportDetail, FeedImportSummary, ImportProgress, ImportStatus, TriggerType } from '../models/import.models';
import { ImportStatusMessage, ProgressUpdateMessage } from './websocket.service';

describe('ImportService', () => {
  let service: ImportService;
  let httpMock: HttpTestingController;

  let mockWebSocketService: jasmine.SpyObj<WebSocketService>;

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

  beforeEach(() => {
    mockWebSocketService = jasmine.createSpyObj<WebSocketService>('WebSocketService', [
      'connect',
      'startHeartbeat',
      'disconnect',
      'subscribeToImportProgress',
      'subscribeToImportStatus',
    ]);
    mockWebSocketService.subscribeToImportProgress.and.returnValue(EMPTY);
    mockWebSocketService.subscribeToImportStatus.and.returnValue(EMPTY);

    TestBed.configureTestingModule({
      imports: [HttpClientTestingModule],
      providers: [
        ImportService,
        { provide: WebSocketService, useValue: mockWebSocketService },
      ],
    });

    service = TestBed.inject(ImportService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpMock.verify();
  });

  it('starts imports and triggers polling', () => {
    spyOn(service, 'startPollingActiveImports');

    service.startImport('f-1').subscribe(result => {
      expect(result.id).toBe('imp-1');
    });

    const req = httpMock.expectOne(`${environment.apiUrl}/feeds/f-1/import`);
    expect(req.request.method).toBe('POST');
    req.flush({ ...baseImport });

    expect(service.startPollingActiveImports).toHaveBeenCalled();
  });

  it('wraps backend errors with user-friendly message', done => {
    service.startImport('f-1').subscribe({
      next: () => fail('Expected error'),
      error: error => {
        expect(error.isBackendError).toBeTrue();
        expect(error.message).toContain('Authentication required');
        done();
      },
    });

    const req = httpMock.expectOne(`${environment.apiUrl}/feeds/f-1/import`);
    req.flush({ message: 'Forbidden' }, { status: 403, statusText: 'Forbidden' });
  });

  it('formats backend error messages for common statuses', () => {
    const getErrorMessage = (service as unknown as {
      getErrorMessage: (error: unknown) => string;
    }).getErrorMessage.bind(service);

    expect(getErrorMessage({ status: 0 })).toContain('Cannot connect');
    expect(getErrorMessage({ status: 404 })).toContain('Feed not found');
    expect(getErrorMessage({ status: 503 })).toContain('temporarily unavailable');
    expect(getErrorMessage({ status: 500, statusText: 'Oops' })).toContain('Oops');
    expect(getErrorMessage({ status: 400, error: { message: 'Bad request' } })).toBe('Bad request');
  });

  it('maps import history and query params', () => {
    service.getFeedImportHistory('f-1', { page: 1, size: 10, status: ImportStatus.FAILED })
      .subscribe(result => {
        expect(result.totalElements).toBe(1);
        expect(result.imports[0].id).toBe('imp-1');
      });

    const req = httpMock.expectOne(request => request.url === `${environment.apiUrl}/feeds/f-1/imports`);
    expect(req.request.params.get('page')).toBe('1');
    expect(req.request.params.get('size')).toBe('10');
    expect(req.request.params.get('status')).toBe(ImportStatus.FAILED);
    req.flush({
      imports: [{ ...baseImport, status: ImportStatus.FAILED }],
      page: { page: 1, size: 10, totalElements: 1, totalPages: 1, hasNext: false, hasPrevious: false },
    });
  });

  it('requests import history with default params', () => {
    service.getFeedImportHistory('f-1').subscribe(result => {
      expect(result.totalElements).toBe(0);
    });

    const req = httpMock.expectOne(request => request.url === `${environment.apiUrl}/feeds/f-1/imports`);
    expect(req.request.params.keys().length).toBe(0);
    req.flush({
      imports: [],
      page: { page: 0, size: 20, totalElements: 0, totalPages: 0, hasNext: false, hasPrevious: false },
    });
  });

  it('refreshes active imports on cancel', () => {
    spyOn(service, 'refreshActiveImports');

    service.cancelImport('imp-1').subscribe(result => {
      expect(result.id).toBe('imp-1');
    });

    const req = httpMock.expectOne(`${environment.apiUrl}/feeds/imports/imp-1`);
    expect(req.request.method).toBe('DELETE');
    req.flush({ ...baseImport, status: ImportStatus.CANCELLED });

    expect(service.refreshActiveImports).toHaveBeenCalled();
  });

  it('updates active imports cache', () => {
    service.getActiveImports().subscribe(imports => {
      expect(imports.length).toBe(1);
    });

    const req = httpMock.expectOne(`${environment.apiUrl}/feeds/imports/active`);
    req.flush({ imports: [{ ...baseImportSummary }], total: 1 });

    service.getActiveImportsObservable().subscribe(imports => {
      expect(imports.length).toBe(1);
    });
  });

  it('finds active import for a feed', () => {
    spyOn(service, 'getActiveImports').and.returnValue(of([
      { ...baseImportSummary, feedOnestopId: 'f-1' },
      { ...baseImportSummary, id: 'imp-2', feedOnestopId: 'f-2' },
    ]));

    service.getActiveImportForFeed('f-2').subscribe(result => {
      expect(result?.id).toBe('imp-2');
    });
  });

  it('returns null when no active import matches', () => {
    spyOn(service, 'getActiveImports').and.returnValue(of([
      { ...baseImportSummary, feedOnestopId: 'f-1' },
    ]));

    service.getActiveImportForFeed('f-2').subscribe(result => {
      expect(result).toBeNull();
    });
  });

  it('retries imports based on existing import data', () => {
    spyOn(service, 'getImport').and.returnValue(of({
      ...baseImportDetail,
      feedOnestopId: 'f-9',
      status: ImportStatus.FAILED,
    }));
    spyOn(service, 'startImport').and.returnValue(of({
      ...baseImport,
      id: 'imp-new',
      feedOnestopId: 'f-9',
    }));

    service.retryImport('imp-1').subscribe(result => {
      expect(result.id).toBe('imp-new');
    });
  });

  it('filters recent and failed imports', () => {
    jasmine.clock().install();
    jasmine.clock().mockDate(new Date('2024-06-02T12:00:00Z'));

    const recent = { ...baseImport, id: 'recent', createdAt: '2024-06-02T11:00:00Z', status: ImportStatus.COMPLETED };
    const old = { ...baseImport, id: 'old', createdAt: '2024-05-30T11:00:00Z', status: ImportStatus.COMPLETED };
    const failed = { ...baseImport, id: 'failed', createdAt: '2024-06-02T10:00:00Z', status: ImportStatus.FAILED };

    spyOn(service, 'getAllImportHistory').and.callFake(options => {
      if (options?.status === ImportStatus.FAILED) {
        return of({ imports: [failed], totalElements: 1, totalPages: 1 });
      }

      return of({ imports: [recent, old, failed], totalElements: 3, totalPages: 1 });
    });

    service.getRecentImports().subscribe(imports => {
      expect(imports.length).toBe(2);
    });

    service.getFailedImports().subscribe(imports => {
      expect(imports.length).toBe(1);
      expect(imports[0].id).toBe('failed');
    });

    jasmine.clock().uninstall();
  });

  it('maps import statistics from active imports', () => {
    spyOn(service, 'getActiveImports').and.returnValue(of([
      { ...baseImportSummary },
      { ...baseImportSummary, id: 'imp-2' },
    ]));

    service.getImportStatistics().subscribe(stats => {
      expect(stats.activeImports).toBe(2);
    });
  });

  it('polls active imports on interval', fakeAsync(() => {
    spyOn(service, 'getActiveImports').and.returnValue(of([]));
    (service as unknown as { pollingInterval: number }).pollingInterval = 10;

    service.startPollingActiveImports();
    tick(0);
    tick(10);

    expect(service.getActiveImports).toHaveBeenCalled();
    service.stopPollingActiveImports();
  }));

  it('skips polling when already active', () => {
    (service as unknown as { isPolling: boolean }).isPolling = true;
    spyOn(service, 'getActiveImports');

    service.startPollingActiveImports();

    expect(service.getActiveImports).not.toHaveBeenCalled();
  });

  it('refreshes active imports on demand', () => {
    spyOn(service, 'getActiveImports').and.returnValue(of([]));

    service.refreshActiveImports();

    expect(service.getActiveImports).toHaveBeenCalled();
  });

  it('checks whether an import is running for a feed', () => {
    spyOn(service, 'getActiveImports').and.returnValue(of([
      { ...baseImportSummary, feedOnestopId: 'f-1' },
    ]));

    service.isImportRunningForFeed('f-1').subscribe(isRunning => {
      expect(isRunning).toBeTrue();
    });
  });

  it('requests all import history with filters', () => {
    service.getAllImportHistory({ page: 2, size: 5, status: ImportStatus.RUNNING, triggerType: TriggerType.AUTOMATIC })
      .subscribe(result => {
        expect(result.totalElements).toBe(1);
      });

    const req = httpMock.expectOne(request => request.url === `${environment.apiUrl}/feeds/imports`);
    expect(req.request.params.get('page')).toBe('2');
    expect(req.request.params.get('size')).toBe('5');
    expect(req.request.params.get('status')).toBe(ImportStatus.RUNNING);
    expect(req.request.params.get('triggerType')).toBe(TriggerType.AUTOMATIC);
    req.flush({
      imports: [{ ...baseImport, status: ImportStatus.RUNNING }],
      page: { page: 2, size: 5, totalElements: 1, totalPages: 1, hasNext: false, hasPrevious: false },
    });
  });

  it('merges progress updates from polling and websocket', fakeAsync(() => {
    spyOn(service, 'getImportProgress').and.returnValue(of({
      progressPercentage: 10,
      totalSteps: 100,
      currentStep: 'Init',
      estimatedTimeRemainingSeconds: null,
    }));

    const progressMessage: ProgressUpdateMessage = {
      progress: {
        importId: 'imp-1',
        feedOnestopId: 'f-1',
        progressPercentage: 20,
        currentStep: 'Step',
        currentStepNumber: 1,
        totalSteps: 8,
        startedAt: '2024-01-01T00:00:00Z',
        lastUpdatedAt: '2024-01-01T00:00:05Z',
        estimatedTimeRemainingSeconds: 5
      }
    };
    mockWebSocketService.subscribeToImportProgress.and.returnValue(of(progressMessage));

    const results: ImportProgress[] = [];
    service.monitorImportProgress('imp-1').subscribe(value => results.push(value));

    tick(0);

    expect(results.length).toBeGreaterThan(0);
    expect(results[0].progressPercentage).toBeDefined();
  }));

  it('merges status updates from polling and websocket', fakeAsync(() => {
    const detail = { ...baseImportDetail, status: ImportStatus.RUNNING };
    spyOn(service, 'getImport').and.returnValue(of(detail));
    const statusMessage: ImportStatusMessage = {
      type: 'IMPORT_STATUS',
      data: {
        importId: 'imp-1',
        status: ImportStatus.RUNNING
      },
      timestamp: '2024-01-01T00:00:00Z'
    };
    mockWebSocketService.subscribeToImportStatus.and.returnValue(of(statusMessage));

    const results: FeedImportDetail[] = [];
    service.monitorImportStatus('imp-1').subscribe(value => results.push(value));

    tick(0);

    expect(results.length).toBeGreaterThan(0);
    expect(results[0].status).toBe(ImportStatus.RUNNING);
  }));
});
