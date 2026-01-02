import { Subject, of, throwError } from 'rxjs';
import { FeedImportsPageComponent } from './feed-imports.page';
import { ImportService } from '../services/import.service';
import { MatSnackBar } from '@angular/material/snack-bar';
import { FeedsMetricsService } from '../services/feeds-metrics.service';
import { FeedsEventsService } from '../services/feeds-events.service';
import { FeedImport, FeedImportSummary, ImportStatus, TriggerType } from '../models';

describe('FeedImportsPageComponent', () => {
  let component: FeedImportsPageComponent;
  let importService: jasmine.SpyObj<ImportService>;
  let snackBar: jasmine.SpyObj<MatSnackBar>;
  let metrics: jasmine.SpyObj<FeedsMetricsService>;
  let events: FeedsEventsService;

  const baseImport: FeedImport = {
    id: 'imp-1',
    feedOnestopId: 'f-1',
    administratorId: null,
    administratorUsername: null,
    status: ImportStatus.COMPLETED,
    triggerType: TriggerType.MANUAL,
    versionSha1: null,
    startedAt: '2024-01-01T00:00:00Z',
    completedAt: '2024-01-01T00:10:00Z',
    fileSizeBytes: null,
    errorMessage: null,
    createdAt: '2024-01-01T00:00:00Z',
    updatedAt: '2024-01-01T00:10:00Z',
  };

  beforeEach(() => {
    spyOn(console, 'error');

    importService = jasmine.createSpyObj<ImportService>('ImportService', [
      'getActiveImportsObservable',
      'getAllImportHistory',
      'refreshActiveImports',
      'cancelImport',
    ]);
    snackBar = jasmine.createSpyObj<MatSnackBar>('MatSnackBar', ['open']);
    metrics = jasmine.createSpyObj<FeedsMetricsService>('FeedsMetricsService', ['setTotalImportElements']);
    events = new FeedsEventsService();

    importService.getActiveImportsObservable.and.returnValue(of([]));
    importService.getAllImportHistory.and.returnValue(of({
      imports: [baseImport],
      totalElements: 1,
      totalPages: 1,
    }));
    importService.cancelImport.and.returnValue(of(baseImport as any));
    snackBar.open.and.returnValue({ onAction: () => new Subject<void>() } as any);

    component = new FeedImportsPageComponent(
      importService,
      snackBar,
      metrics,
      events
    );
  });

  it('loads history and refreshes active imports on init', () => {
    component.ngOnInit();

    expect(importService.getAllImportHistory).toHaveBeenCalled();
    expect(importService.refreshActiveImports).toHaveBeenCalled();
  });

  it('updates state on history load', () => {
    component.loadImportHistory(2);

    expect(component.importHistory.length).toBe(1);
    expect(component.importHistoryPage).toBe(2);
    expect(component.totalImportElements).toBe(1);
    expect(metrics.setTotalImportElements).toHaveBeenCalledWith(1);
  });

  it('handles history load failures', () => {
    importService.getAllImportHistory.and.returnValue(throwError(() => new Error('fail')));

    component.loadImportHistory();

    expect(component.loadingHistory).toBeFalse();
    expect(snackBar.open).toHaveBeenCalledWith('Failed to load import history', 'Close', { duration: 4000 });
  });

  it('cancels imports and refreshes data', () => {
    spyOn(component, 'loadImportHistory');

    component.cancelImport('imp-1');

    expect(importService.cancelImport).toHaveBeenCalledWith('imp-1');
    expect(importService.refreshActiveImports).toHaveBeenCalled();
    expect(component.loadImportHistory).toHaveBeenCalled();
  });

  it('handles cancel errors with retry prompt', () => {
    importService.cancelImport.and.returnValue(throwError(() => ({ message: 'nope' })));

    component.cancelImport('imp-1');

    expect(snackBar.open).toHaveBeenCalled();
  });

  it('uses backend error detail when cancel fails', () => {
    importService.cancelImport.and.returnValue(throwError(() => ({ error: { message: 'backend issue' } })));

    component.cancelImport('imp-1');

    const message = snackBar.open.calls.mostRecent().args[0] as string;
    expect(message).toContain('backend issue');
  });

  it('falls back to a default cancel error message', () => {
    importService.cancelImport.and.returnValue(throwError(() => ({})));

    component.cancelImport('imp-1');

    const message = snackBar.open.calls.mostRecent().args[0] as string;
    expect(message).toContain('Unknown error occurred');
  });

  it('refreshes history on events', () => {
    spyOn(component, 'loadImportHistory');

    component.ngOnInit();
    events.triggerRefresh();

    expect(component.loadImportHistory).toHaveBeenCalledWith(component.importHistoryPage);
    expect(importService.refreshActiveImports).toHaveBeenCalled();
  });
});
