import { fakeAsync, tick } from '@angular/core/testing';
import { firstValueFrom, of, throwError } from 'rxjs';
import { RegionListComponent } from './region-list.component';
import { RegionService } from '../../feeds/services/region.service';
import { ImportService } from '../../feeds/services/import.service';
import { SchedulerService } from '../../feeds/services/scheduler.service';
import { FeedImportSummary, ImportStatus, TriggerType } from '../../feeds/models/import.models';
import { MetropolitanRegion } from '../../feeds/models/region.models';

describe('RegionListComponent', () => {
  let component: RegionListComponent;
  let regionService: jasmine.SpyObj<RegionService>;
  let importService: jasmine.SpyObj<ImportService>;
  let schedulerService: jasmine.SpyObj<SchedulerService>;
  let snackBar: { open: jasmine.Spy };

  const baseRegion: MetropolitanRegion = {
    regionOnestopId: 'r-test',
    name: 'Test Region',
    adm0Name: 'United States',
    adm1Name: 'California',
    autoUpdateEnabled: false,
    feedCount: 2,
    lastCheckAt: null,
    createdAt: '2024-01-01T00:00:00Z',
    updatedAt: '2024-01-01T00:00:00Z',
  };

  const baseImportSummary: FeedImportSummary = {
    id: 'imp-1',
    feedOnestopId: 'f-1',
    feedName: 'Feed 1',
    regionName: 'Test Region',
    status: ImportStatus.RUNNING,
    triggerType: TriggerType.MANUAL,
    startedAt: '2024-01-01T00:00:00Z',
    completedAt: null,
    fileSizeBytes: null,
    errorMessage: null,
    progress: null,
  };

  beforeEach(() => {
    regionService = jasmine.createSpyObj<RegionService>('RegionService', [
      'listRegions',
      'clearCache',
      'sortWithCanadianPriority',
    ]);
    importService = jasmine.createSpyObj<ImportService>('ImportService', [
      'getActiveImports',
      'startPollingActiveImports',
      'stopPollingActiveImports',
      'getActiveImportsObservable',
    ]);
    schedulerService = jasmine.createSpyObj<SchedulerService>('SchedulerService', [
      'enableFeedAutoUpdate',
      'disableFeedAutoUpdate',
      'checkFeedUpdate',
      'getAllFeedVersions',
    ]);
    snackBar = { open: jasmine.createSpy('open') };

    regionService.listRegions.and.returnValue(of([baseRegion]));
    regionService.sortWithCanadianPriority.and.callFake(regions => regions);
    importService.getActiveImports.and.returnValue(of([baseImportSummary]));
    importService.getActiveImportsObservable.and.returnValue(of([baseImportSummary]));
    schedulerService.enableFeedAutoUpdate.and.returnValue(of(void 0));
    schedulerService.disableFeedAutoUpdate.and.returnValue(of(void 0));
    schedulerService.checkFeedUpdate.and.returnValue(of(true));
    schedulerService.getAllFeedVersions.and.returnValue(of([]));

    component = new RegionListComponent(
      regionService,
      importService,
      schedulerService,
      snackBar as any
    );
  });

  it('loads regions and active imports on init', () => {
    component.ngOnInit();

    expect(regionService.listRegions).toHaveBeenCalled();
    expect(importService.getActiveImports).toHaveBeenCalled();
    expect(importService.startPollingActiveImports).toHaveBeenCalled();
    expect(component.regions$.value.length).toBe(1);
    expect(component.activeImports$.value.length).toBe(1);
    expect(component.isLoading$.value).toBeFalse();
  });

  it('handles region load errors', () => {
    regionService.listRegions.and.returnValue(throwError(() => new Error('fail')));

    component.ngOnInit();

    expect(component.isLoading$.value).toBeFalse();
    expect(component.error$.value).toBe('Failed to load regions. Please try again.');
  });

  it('filters regions by search term and auto-update flag', () => {
    const regions: MetropolitanRegion[] = [
      { ...baseRegion, regionOnestopId: 'r-1', name: 'Toronto', autoUpdateEnabled: true },
      { ...baseRegion, regionOnestopId: 'r-2', name: 'Austin', autoUpdateEnabled: false },
    ];

    const filtered = (component as any).filterRegions(regions, 'tor', true) as MetropolitanRegion[];
    expect(filtered.length).toBe(1);
    expect(filtered[0].regionOnestopId).toBe('r-1');
  });

  it('emits selection and details events', () => {
    let selected: MetropolitanRegion | undefined;
    let details: MetropolitanRegion | undefined;

    component.regionSelected.subscribe(region => (selected = region));
    component.regionDetailsRequested.subscribe(region => (details = region));

    component.selectRegion(baseRegion);
    component.viewRegionDetails(baseRegion);

    expect(selected).toEqual(baseRegion);
    expect(details).toEqual(baseRegion);
  });

  it('refreshes regions and resets cache', () => {
    component.refreshRegions();

    expect(regionService.clearCache).toHaveBeenCalled();
    expect(regionService.listRegions).toHaveBeenCalled();
  });

  it('handles discovery completion', () => {
    component.selectedRegion = baseRegion;

    spyOn(component, 'refreshRegions');
    let emitted: MetropolitanRegion | undefined;
    component.regionSelected.subscribe(region => (emitted = region));

    component.handleDiscoveryCompleted(baseRegion, {
      regionOnestopId: baseRegion.regionOnestopId,
      feedsDiscovered: 1,
      feedsCreated: 1,
      feedsUpdated: 0,
      errors: [],
    });

    expect(component.refreshRegions).toHaveBeenCalled();
    expect(emitted).toEqual(baseRegion);
  });

  it('tracks regions and formats labels', () => {
    expect(component.trackByRegionId(0, baseRegion)).toBe('r-test');
    expect(component.getDisplayName(baseRegion)).toBe('Test Region, California, United States');
  });

  it('calculates active import status', () => {
    component.activeImports$.next([baseImportSummary]);

    expect(component.hasActiveImport(baseRegion)).toBeTrue();
    expect(component.getActiveImportCount(baseRegion)).toBe(1);
  });

  it('finds active imports by feed id match', () => {
    component.activeImports$.next([
      { ...baseImportSummary, regionName: 'Other', feedOnestopId: 'f-r-1-demo' },
    ]);

    expect(component.hasActiveImport({ ...baseRegion, regionOnestopId: 'r-1' })).toBeTrue();
  });

  it('computes total feeds from filtered regions', async () => {
    component.regions$.next([
      { ...baseRegion, regionOnestopId: 'r-1', feedCount: 3 },
      { ...baseRegion, regionOnestopId: 'r-2', feedCount: 5 },
    ]);

    const total = await firstValueFrom(component.getTotalFeeds());
    expect(total).toBe(8);
  });

  it('toggles auto-update and shows snackbars', () => {
    spyOn(component, 'refreshRegions');

    component.toggleAutoUpdate(baseRegion, true);

    expect(schedulerService.enableFeedAutoUpdate).toHaveBeenCalledWith('r-test');
    expect(snackBar.open).toHaveBeenCalled();
    expect(component.refreshRegions).toHaveBeenCalled();
  });

  it('handles auto-update errors', () => {
    schedulerService.enableFeedAutoUpdate.and.returnValue(throwError(() => new Error('fail')));

    component.toggleAutoUpdate(baseRegion, true);

    expect(component.isUpdatingAutoUpdate.has('r-test')).toBeFalse();
    expect(snackBar.open).toHaveBeenCalledWith('Failed to update auto-update setting', 'Close', { duration: 3000 });
  });

  it('checks for updates and stores version status', () => {
    component.checkForUpdates(baseRegion);

    expect(schedulerService.checkFeedUpdate).toHaveBeenCalled();
    const status = component.getVersionStatus(baseRegion);
    expect(status.hasUpdate).toBeTrue();
  });

  it('handles update check errors', () => {
    schedulerService.checkFeedUpdate.and.returnValue(throwError(() => new Error('fail')));

    component.checkForUpdates(baseRegion);

    expect(component.isCheckingUpdates.has('r-test')).toBeFalse();
    expect(snackBar.open).toHaveBeenCalledWith('Failed to check for updates', 'Close', { duration: 3000 });
  });

  it('returns existing version status data', () => {
    const now = new Date('2024-06-01T12:00:00Z');
    component.feedVersions.set('r-test', { lastChecked: now, hasUpdate: true });

    const status = component.getVersionStatus(baseRegion);

    expect(status.lastChecked).toBe(now);
    expect(status.hasUpdate).toBeTrue();
  });

  it('stops polling on destroy', () => {
    component.ngOnDestroy();

    expect(importService.stopPollingActiveImports).toHaveBeenCalled();
  });
});
