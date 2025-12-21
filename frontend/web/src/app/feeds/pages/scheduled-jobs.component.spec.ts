import { fakeAsync, tick } from '@angular/core/testing';
import { firstValueFrom, of, throwError } from 'rxjs';
import { ScheduledJobsComponent } from './scheduled-jobs.component';
import { SchedulerService } from '../services/scheduler.service';
import { ImportService } from '../services/import.service';
import { FeedImport, ImportStatus, TriggerType } from '../models/import.models';
import { MatSnackBar } from '@angular/material/snack-bar';
import { MatDialog } from '@angular/material/dialog';

describe('ScheduledJobsComponent', () => {
  let component: ScheduledJobsComponent;
  let schedulerService: jasmine.SpyObj<SchedulerService>;
  let importService: jasmine.SpyObj<ImportService>;
  let snackBar: jasmine.SpyObj<MatSnackBar>;
  let dialog: jasmine.SpyObj<MatDialog>;

  const baseImport: FeedImport = {
    id: 'imp-1',
    feedOnestopId: 'f-1',
    administratorId: null,
    administratorUsername: null,
    triggerType: TriggerType.AUTOMATIC,
    status: ImportStatus.COMPLETED,
    versionSha1: null,
    startedAt: '2024-06-01T12:00:00Z',
    completedAt: '2024-06-01T12:02:00Z',
    fileSizeBytes: null,
    errorMessage: null,
    createdAt: '2024-06-01T12:00:00Z',
    updatedAt: '2024-06-01T12:02:00Z',
  };

  beforeEach(() => {
    schedulerService = jasmine.createSpyObj<SchedulerService>('SchedulerService', [
      'getSchedulerStatus',
      'getImportStats',
      'getAllFeedVersions',
      'triggerManualCheck',
      'refreshFeedVersion',
      'checkFeedUpdate',
    ]);
    importService = jasmine.createSpyObj<ImportService>('ImportService', ['getRecentImports']);
    snackBar = jasmine.createSpyObj<MatSnackBar>('MatSnackBar', ['open']);
    dialog = jasmine.createSpyObj<MatDialog>('MatDialog', ['open']);

    schedulerService.getSchedulerStatus.and.returnValue(of({
      enabled: true,
      totalActiveFeeds: 2,
      feedsCheckedInLast24Hours: 1,
      nextScheduledRun: '2024-06-01T13:00:00Z',
    }));
    schedulerService.getImportStats.and.returnValue(of({
      totalAutomaticImportsLast24h: 4,
      successfulImportsLast24h: 3,
      failedImportsLast24h: 1,
      currentlyRunningAutoImports: 0,
    }));
    schedulerService.getAllFeedVersions.and.returnValue(of([]));
    schedulerService.triggerManualCheck.and.returnValue(of({
      success: true,
      checkedCount: 5,
      updatesTriggered: 2,
      errorCount: 0,
      errors: [],
      message: 'done',
    }));
    schedulerService.refreshFeedVersion.and.returnValue(of({
      feedOnestopId: 'f-1',
      hasUpdate: false,
      status: 'available',
    } as any));
    schedulerService.checkFeedUpdate.and.returnValue(of(true));

    importService.getRecentImports.and.returnValue(of([
      { ...baseImport, id: 'auto-1', triggerType: TriggerType.AUTOMATIC },
      { ...baseImport, id: 'manual-1', triggerType: TriggerType.MANUAL },
      { ...baseImport, id: 'auto-2', triggerType: TriggerType.AUTOMATIC, startedAt: '2024-06-02T12:00:00Z' },
    ]));

    component = new ScheduledJobsComponent(
      schedulerService,
      importService,
      snackBar,
      dialog
    );
  });

  it('initializes data streams and recent imports', async () => {
    const recent = await firstValueFrom(component.recentImports$);

    expect(recent.length).toBe(2);
    expect(recent[0].id).toBe('auto-2');
    expect(schedulerService.getSchedulerStatus).toHaveBeenCalled();
    expect(schedulerService.getImportStats).toHaveBeenCalled();
    expect(schedulerService.getAllFeedVersions).toHaveBeenCalled();
  });

  it('refreshes data on interval', fakeAsync(() => {
    spyOn(component, 'refreshData');
    (component as any).refreshInterval = 10;

    component.ngOnInit();
    tick(10);

    expect(component.refreshData).toHaveBeenCalled();
  }));

  it('refreshes data streams on demand', () => {
    component.refreshData();

    expect(schedulerService.getSchedulerStatus).toHaveBeenCalled();
    expect(schedulerService.getImportStats).toHaveBeenCalled();
    expect(schedulerService.getAllFeedVersions).toHaveBeenCalled();
  });

  it('triggers manual checks and shows success message', () => {
    spyOn(component, 'refreshData');

    component.triggerManualCheck();

    expect(component.triggering).toBeFalse();
    expect(snackBar.open).toHaveBeenCalled();
    expect(component.refreshData).toHaveBeenCalled();
  });

  it('handles manual check errors', () => {
    schedulerService.triggerManualCheck.and.returnValue(throwError(() => new Error('fail')));

    component.triggerManualCheck();

    expect(component.triggering).toBeFalse();
    expect(snackBar.open).toHaveBeenCalledWith('Failed to trigger manual check', 'Close', { duration: 3000 });
  });

  it('refreshes feed version and checks updates', () => {
    spyOn(component, 'refreshData');

    component.refreshFeedVersion('f-1');
    component.checkFeedUpdate('f-1');

    expect(snackBar.open).toHaveBeenCalled();
    expect(component.refreshData).toHaveBeenCalled();
  });

  it('maps status chip labels and classes', () => {
    expect(component.getStatusChipClass('available')).toBe('chip-success');
    expect(component.getStatusChipClass('not_found')).toBe('chip-warning');
    expect(component.getStatusLabel('api_unavailable')).toBe('API Unavailable');
    expect(component.getStatusLabel('unknown')).toBe('unknown');
  });

  it('maps import status chips and durations', () => {
    expect(component.getImportStatusChipClass(ImportStatus.COMPLETED)).toBe('chip-success');
    expect(component.getImportStatusChipClass(ImportStatus.RUNNING)).toBe('chip-warning');
    expect(component.getImportStatusChipClass(ImportStatus.PENDING)).toBe('chip-neutral');

    expect(component.calculateDuration(baseImport)).toBe('2m');

    const running: FeedImport = { ...baseImport, completedAt: null, status: ImportStatus.RUNNING };
    expect(component.calculateDuration(running)).toBe('Running...');

    const missingStart: FeedImport = { ...baseImport, startedAt: null, completedAt: '2024-06-01T12:01:00Z' };
    expect(component.calculateDuration(missingStart)).toBe('-');
  });

  it('cleans up subscriptions on destroy', () => {
    component.ngOnDestroy();
    expect((component as any).destroy$.isStopped).toBeTrue();
  });
});
