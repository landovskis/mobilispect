import { Subject, of, throwError } from 'rxjs';
import { TestBed } from '@angular/core/testing';
import { FeedImportsPageComponent } from './feed-imports.page';
import { ImportService } from '../services/import.service';
import { MatSnackBar } from '@angular/material/snack-bar';
import { FeedsMetricsService } from '../services/feeds-metrics.service';
import { FeedsEventsService } from '../services/feeds-events.service';
import { FeedImportDetail, ImportStatus, TriggerType } from '../models';

describe('FeedImportsPageComponent', () => {
  let component: FeedImportsPageComponent;
  let importService: jasmine.SpyObj<ImportService>;
  let snackBar: jasmine.SpyObj<MatSnackBar>;
  let metrics: jasmine.SpyObj<FeedsMetricsService>;
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

  beforeEach(() => {
    importService = jasmine.createSpyObj<ImportService>('ImportService', [
      'getActiveImportsObservable',
      'getAllImportHistory',
      'refreshActiveImports',
      'cancelImport',
    ]);
    snackBar = jasmine.createSpyObj<MatSnackBar>('MatSnackBar', ['open']);
    metrics = jasmine.createSpyObj<FeedsMetricsService>('FeedsMetricsService', [
      'setTotalImportElements',
    ]);
    events = new FeedsEventsService();

    importService.getActiveImportsObservable.and.returnValue(of([]));
    importService.getAllImportHistory.and.returnValue(
      of({
        imports: [baseImport],
        totalElements: 1,
        totalPages: 1,
      }),
    );
    importService.cancelImport.and.returnValue(of(baseImport));
    snackBar.open.and.returnValue({
      onAction: () => new Subject<void>(),
    } as any);

    TestBed.configureTestingModule({
      imports: [FeedImportsPageComponent],
      providers: [
        { provide: ImportService, useValue: importService },
        { provide: FeedsMetricsService, useValue: metrics },
        { provide: FeedsEventsService, useValue: events },
        { provide: MatSnackBar, useValue: snackBar },
      ],
    });
    component = TestBed.createComponent(
      FeedImportsPageComponent,
    ).componentInstance;
  });

  it('loads history and refreshes active imports on init', () => {
    spyOn(component, 'loadImportHistory');

    component.ngOnInit();

    expect(component.loadImportHistory).toHaveBeenCalledWith(0);
    expect(importService.refreshActiveImports).toHaveBeenCalled();
  });

  it('refreshes history when events trigger', () => {
    spyOn(component, 'loadImportHistory');

    component.ngOnInit();
    events.triggerRefresh();

    expect(component.loadImportHistory).toHaveBeenCalledWith(0);
    expect(importService.refreshActiveImports).toHaveBeenCalledTimes(2);
  });

  it('updates state and metrics on history load', () => {
    component.loadImportHistory(2);

    expect(component.importHistory).toEqual([baseImport]);
    expect(component.importHistoryPage).toBe(2);
    expect(component.totalImportElements).toBe(1);
    expect(metrics.setTotalImportElements).toHaveBeenCalledWith(1);
    expect(component.loadingHistory).toBeFalse();
  });

  it('handles history load failures with a snackbar', () => {
    importService.getAllImportHistory.and.returnValue(
      throwError(() => new Error('fail')),
    );

    component.loadImportHistory();

    expect(component.loadingHistory).toBeFalse();
    expect(snackBar.open).toHaveBeenCalledWith(
      'Failed to load import history',
      'Close',
      { duration: 4000 },
    );
  });

  it('cancels imports and refreshes data', () => {
    spyOn(component, 'loadImportHistory');

    component.cancelImport('imp-1');

    expect(importService.cancelImport).toHaveBeenCalledWith('imp-1');
    expect(importService.refreshActiveImports).toHaveBeenCalled();
    expect(component.loadImportHistory).toHaveBeenCalledWith(0);
    expect(snackBar.open).toHaveBeenCalledWith(
      '✅ Import cancelled successfully',
      'Close',
      { duration: 4000 },
    );
  });

  it('retries cancel on action when cancellation fails', () => {
    const retry$ = new Subject<void>();
    snackBar.open.and.returnValue({ onAction: () => retry$ } as any);
    importService.cancelImport.and.returnValues(
      throwError(() => ({ error: { message: 'backend issue' } })),
      of(baseImport),
    );
    spyOn(component, 'loadImportHistory');

    component.cancelImport('imp-1');
    retry$.next();

    expect(importService.cancelImport).toHaveBeenCalledTimes(2);
    expect(component.loadImportHistory).toHaveBeenCalledWith(0);
  });

  it('shows backend error detail when cancel fails', () => {
    importService.cancelImport.and.returnValue(
      throwError(() => ({ error: { message: 'backend issue' } })),
    );

    component.cancelImport('imp-1');

    const message = snackBar.open.calls.mostRecent().args[0] as string;
    expect(message).toContain('backend issue');
  });
});
