import { Subject, of, throwError } from 'rxjs';
import { TestBed } from '@angular/core/testing';
import { FeedImportsPageComponent } from './feed-imports.page';
import { ImportService } from '../services/import.service';
import { MatSnackBar, MatSnackBarModule } from '@angular/material/snack-bar';
import { FeedsMetricsService } from '../services/feeds-metrics.service';
import { FeedsEventsService } from '../services/feeds-events.service';
import { FeedImportDetail, ImportStatus, TriggerType } from '../models';
import { vi } from 'vitest';

describe('FeedImportsPageComponent', () => {
  let component: FeedImportsPageComponent;
  let importService: ImportService;
  let snackBar: MatSnackBar;
  let metrics: FeedsMetricsService;
  let events: FeedsEventsService;

  const baseImport: FeedImportDetail = {
    id: 'imp-1',
    feedOnestopId: 'f-1',
    feedName: 'Feed 1',
    regionName: 'Region 1',
    administratorId: null,
    administratorUsername: null,
    status: ImportStatus.COMPLETED,
    triggerType: TriggerType.MANUAL,
    versionSha1: null,
    startedAt: '2024-01-01T00:00:00Z',
    completedAt: '2024-01-01T00:10:00Z',
    fileSizeBytes: null,
    errorMessage: null,
    progress: null,
    createdAt: '2024-01-01T00:00:00Z',
    updatedAt: '2024-01-01T00:10:00Z',
  };

  const setup = () => {
    component.ngOnInit();
  };

  beforeEach(() => {
    importService = {
      getActiveImportsObservable: vi.fn(),
      getAllImportHistory: vi.fn(),
      refreshActiveImports: vi.fn(),
      cancelImport: vi.fn(),
    } as unknown as ImportService;
    snackBar = {
      open: vi.fn(),
    } as unknown as MatSnackBar;
    metrics = {
      setTotalImportElements: vi.fn(),
    } as unknown as FeedsMetricsService;
    events = new FeedsEventsService();

    vi.mocked(importService.getActiveImportsObservable).mockReturnValue(of([]));
    vi.mocked(importService.getAllImportHistory).mockReturnValue(
      of({
        imports: [baseImport],
        totalElements: 1,
        totalPages: 1,
      })
    );
    vi.mocked(importService.cancelImport).mockReturnValue(of(baseImport));
    vi.mocked(snackBar.open).mockReturnValue({
      onAction: () => new Subject<void>(),
    } as any);

    TestBed.overrideComponent(FeedImportsPageComponent, {
      remove: { imports: [MatSnackBarModule] },
    });

    TestBed.configureTestingModule({
      imports: [FeedImportsPageComponent],
      providers: [
        { provide: ImportService, useValue: importService },
        { provide: FeedsMetricsService, useValue: metrics },
        { provide: FeedsEventsService, useValue: events },
        { provide: MatSnackBar, useValue: snackBar },
      ],
    });
    component = TestBed.createComponent(FeedImportsPageComponent).componentInstance;
  });

  it('initializes history and refreshes active imports', () => {
    vi.spyOn(component, 'loadImportHistory').mockImplementation(() => {});

    setup();

    expect(component.loadImportHistory).toHaveBeenCalled();
    expect(importService.refreshActiveImports).toHaveBeenCalled();
  });

  it('refreshes data when events emit', () => {
    vi.spyOn(component, 'loadImportHistory').mockImplementation(() => {});

    setup();
    events.triggerRefresh();

    expect(component.loadImportHistory).toHaveBeenCalledWith(0);
    expect(importService.refreshActiveImports).toHaveBeenCalledTimes(2);
  });

  it('updates history state and metrics on load', () => {
    component.loadImportHistory(2);

    expect(component.loadingHistory).toBe(false);
    expect(component.importHistoryPage).toBe(2);
    expect(component.importHistory).toEqual([baseImport]);
    expect(component.totalImportElements).toBe(1);
    expect(metrics.setTotalImportElements).toHaveBeenCalledWith(1);
  });

  it('shows snackbar when history load fails', () => {
    vi.mocked(importService.getAllImportHistory).mockReturnValue(
      throwError(() => new Error('fail'))
    );

    component.loadImportHistory();

    expect(component.loadingHistory).toBe(false);
    expect(snackBar.open).toHaveBeenCalledWith('Failed to load import history', 'Close', {
      duration: 4000,
    });
  });

  it('cancels import and refreshes history', () => {
    vi.spyOn(component, 'loadImportHistory').mockImplementation(() => {});

    component.cancelImport('imp-1');

    expect(importService.cancelImport).toHaveBeenCalledWith('imp-1');
    expect(importService.refreshActiveImports).toHaveBeenCalled();
    expect(component.loadImportHistory).toHaveBeenCalledWith(0);
    const calls = vi.mocked(snackBar.open).mock.calls;
    const lastCall = calls[calls.length - 1];
    expect(lastCall).toEqual(['✅ Import cancelled successfully', 'Close', { duration: 4000 }]);
  });

  it('retries cancel on snackbar action after failure', () => {
    const retry$ = new Subject<void>();
    vi.mocked(snackBar.open).mockReturnValue({ onAction: () => retry$ } as any);
    vi.mocked(importService.cancelImport)
      .mockReturnValueOnce(throwError(() => ({ error: { message: 'backend issue' } })))
      .mockReturnValueOnce(of(baseImport));
    vi.spyOn(component, 'loadImportHistory').mockImplementation(() => {});

    component.cancelImport('imp-1');
    retry$.next();

    expect(importService.cancelImport).toHaveBeenCalledTimes(2);
    expect(component.loadImportHistory).toHaveBeenCalledWith(0);
  });

  it('surfaces backend error detail on cancel failure', () => {
    vi.mocked(importService.cancelImport).mockReturnValue(
      throwError(() => ({ error: { message: 'backend issue' } }))
    );

    component.cancelImport('imp-1');

    const calls = vi.mocked(snackBar.open).mock.calls;
    const lastCall = calls[calls.length - 1];
    expect(lastCall[0] as string).toContain('backend issue');
  });
});
