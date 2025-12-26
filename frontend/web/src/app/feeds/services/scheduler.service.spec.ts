import { TestBed } from '@angular/core/testing';
import { HttpClientTestingModule, HttpTestingController } from '@angular/common/http/testing';
import { SchedulerService } from './scheduler.service';
import { environment } from '../../../environments/environment';

describe('SchedulerService', () => {
  let service: SchedulerService;
  let httpMock: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [HttpClientTestingModule],
      providers: [SchedulerService],
    });

    service = TestBed.inject(SchedulerService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpMock.verify();
  });

  it('converts scheduler status dates', () => {
    service.getSchedulerStatus().subscribe(status => {
      expect(status.lastRunTime instanceof Date).toBeTrue();
    });

    const req = httpMock.expectOne(`${environment.apiUrl}/feeds/scheduler/status`);
    req.flush({
      enabled: true,
      totalActiveFeeds: 2,
      feedsCheckedInLast24Hours: 1,
      nextScheduledRun: '2024-06-01T12:00:00Z',
      lastRunTime: '2024-05-31T12:00:00Z',
    });
  });

  it('converts import stats and version timestamps', () => {
    service.getImportStats().subscribe(stats => {
      expect(stats.lastAutomaticImportTime instanceof Date).toBeTrue();
    });

    const statsReq = httpMock.expectOne(`${environment.apiUrl}/feeds/scheduler/stats`);
    statsReq.flush({
      totalAutomaticImportsLast24h: 4,
      successfulImportsLast24h: 3,
      failedImportsLast24h: 1,
      currentlyRunningAutoImports: 0,
      lastAutomaticImportTime: '2024-06-01T06:00:00Z',
    });

    service.getAllFeedVersions().subscribe(versions => {
      expect(versions[0].lastCheckedAt instanceof Date).toBeTrue();
      expect(versions[0].lastUpdatedAt instanceof Date).toBeTrue();
    });

    const versionsReq = httpMock.expectOne(`${environment.apiUrl}/feeds/scheduler/versions`);
    versionsReq.flush([
      {
        feedOnestopId: 'f-abc-test',
        currentVersionSha1: 'abc',
        latestVersionSha1: 'def',
        hasUpdate: true,
        lastCheckedAt: '2024-06-01T00:00:00Z',
        lastUpdatedAt: '2024-05-31T00:00:00Z',
        status: 'available',
      },
    ]);
  });

  it('handles null scheduler timestamps and config updates', () => {
    service.getSchedulerStatus().subscribe(status => {
      expect(status.lastRunTime).toBeUndefined();
    });

    const statusReq = httpMock.expectOne(`${environment.apiUrl}/feeds/scheduler/status`);
    statusReq.flush({
      enabled: true,
      totalActiveFeeds: 2,
      feedsCheckedInLast24Hours: 1,
      nextScheduledRun: '2024-06-01T12:00:00Z',
      lastRunTime: null,
    });

    service.getImportStats().subscribe(stats => {
      expect(stats.lastAutomaticImportTime).toBeUndefined();
    });

    const statsReq = httpMock.expectOne(`${environment.apiUrl}/feeds/scheduler/stats`);
    statsReq.flush({
      totalAutomaticImportsLast24h: 0,
      successfulImportsLast24h: 0,
      failedImportsLast24h: 0,
      currentlyRunningAutoImports: 0,
      lastAutomaticImportTime: null,
    });

    service.getAutoUpdateConfig().subscribe(config => {
      expect(config.globalAutoUpdateEnabled).toBeTrue();
    });

    const configReq = httpMock.expectOne(`${environment.apiUrl}/feeds/scheduler/config`);
    configReq.flush({
      globalAutoUpdateEnabled: true,
      defaultCheckIntervalHours: 6,
      maxConcurrentImports: 2,
      notifyOnFailures: true,
      retryFailedImports: 1,
    });

    service.updateAutoUpdateConfig({
      globalAutoUpdateEnabled: false,
      defaultCheckIntervalHours: 12,
      maxConcurrentImports: 1,
      notifyOnFailures: false,
      retryFailedImports: 0,
    }).subscribe(config => {
      expect(config.globalAutoUpdateEnabled).toBeFalse();
    });

    const updateReq = httpMock.expectOne(`${environment.apiUrl}/feeds/scheduler/config`);
    expect(updateReq.request.method).toBe('PUT');
    updateReq.flush({
      globalAutoUpdateEnabled: false,
      defaultCheckIntervalHours: 12,
      maxConcurrentImports: 1,
      notifyOnFailures: false,
      retryFailedImports: 0,
    });
  });

  it('loads single feed versions and refreshes', () => {
    service.getFeedVersion('f-1').subscribe(version => {
      expect(version.lastCheckedAt).toBeUndefined();
      expect(version.lastUpdatedAt).toBeUndefined();
    });

    const feedReq = httpMock.expectOne(`${environment.apiUrl}/feeds/scheduler/versions/f-1`);
    feedReq.flush({
      feedOnestopId: 'f-1',
      currentVersionSha1: 'abc',
      latestVersionSha1: 'def',
      hasUpdate: false,
      lastCheckedAt: null,
      lastUpdatedAt: null,
      status: 'available',
    });

    service.refreshFeedVersion('f-1').subscribe(version => {
      expect(version.lastCheckedAt instanceof Date).toBeTrue();
    });

    const refreshReq = httpMock.expectOne(`${environment.apiUrl}/feeds/scheduler/versions/f-1/refresh`);
    expect(refreshReq.request.method).toBe('POST');
    refreshReq.flush({
      feedOnestopId: 'f-1',
      currentVersionSha1: 'abc',
      latestVersionSha1: 'def',
      hasUpdate: true,
      lastCheckedAt: '2024-06-01T00:00:00Z',
      lastUpdatedAt: null,
      status: 'available',
    });
  });

  it('handles missing timestamps in feed versions', () => {
    service.getAllFeedVersions().subscribe(versions => {
      expect(versions[0].lastCheckedAt).toBeUndefined();
      expect(versions[0].lastUpdatedAt).toBeUndefined();
    });

    const versionsReq = httpMock.expectOne(`${environment.apiUrl}/feeds/scheduler/versions`);
    versionsReq.flush([
      {
        feedOnestopId: 'f-null',
        currentVersionSha1: 'abc',
        latestVersionSha1: 'def',
        hasUpdate: false,
        lastCheckedAt: null,
        lastUpdatedAt: null,
        status: 'available',
      },
    ]);
  });

  it('triggers manual checks and toggles auto update', () => {
    service.triggerManualCheck().subscribe(result => {
      expect(result.checkedCount).toBe(4);
    });

    const manualReq = httpMock.expectOne(`${environment.apiUrl}/feeds/scheduler/manual-check`);
    expect(manualReq.request.method).toBe('POST');
    manualReq.flush({
      success: true,
      checkedCount: 4,
      updatesTriggered: 1,
      errorCount: 0,
      errors: [],
      message: 'ok',
    });

    service.enableFeedAutoUpdate('f-2').subscribe();
    const enableReq = httpMock.expectOne(`${environment.apiUrl}/feeds/scheduler/feeds/f-2/auto-update/enable`);
    expect(enableReq.request.method).toBe('POST');
    enableReq.flush({});

    service.disableFeedAutoUpdate('f-2').subscribe();
    const disableReq = httpMock.expectOne(`${environment.apiUrl}/feeds/scheduler/feeds/f-2/auto-update/disable`);
    expect(disableReq.request.method).toBe('POST');
    disableReq.flush({});
  });

  it('maps feed update checks', () => {
    service.checkFeedUpdate('f-abc-test').subscribe(hasUpdate => {
      expect(hasUpdate).toBeTrue();
    });

    const req = httpMock.expectOne(`${environment.apiUrl}/feeds/scheduler/feeds/f-abc-test/check-update`);
    req.flush({ hasUpdate: true });
  });
});
