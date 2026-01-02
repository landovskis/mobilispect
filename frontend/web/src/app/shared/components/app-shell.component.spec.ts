import { ComponentFixture, TestBed } from '@angular/core/testing';
import { AppShellComponent } from './app-shell.component';
import { BreakpointObserver } from '@angular/cdk/layout';
import { MatSnackBarModule } from '@angular/material/snack-bar';
import { ImportService } from '../../feeds/services/import.service';
import { FeedsMetricsService } from '../../feeds/services/feeds-metrics.service';
import { FeedsEventsService } from '../../feeds/services/feeds-events.service';
import { RegionService } from '../../feeds/services/region.service';
import { BehaviorSubject, firstValueFrom, of } from 'rxjs';
import { RouterTestingModule } from '@angular/router/testing';
import { NoopAnimationsModule } from '@angular/platform-browser/animations';
import { FeedImportSummary } from '../../feeds/models';

describe('AppShellComponent', () => {
  let component: AppShellComponent;
  let fixture: ComponentFixture<AppShellComponent>;

  const mockImportService = {
    getActiveImportsObservable: () => of<FeedImportSummary[]>([]),
    refreshActiveImports: jasmine.createSpy('refreshActiveImports'),
  };

  const mockMetricsService = {
    discoverFeedCount$: of(0),
    totalImportElements$: of(0),
    resetSelectedRegion: jasmine.createSpy('resetSelectedRegion'),
  };

  const mockEventsService = {
    triggerRefresh: jasmine.createSpy('triggerRefresh'),
  };

  const mockRegionService = {
    clearCache: jasmine.createSpy('clearCache'),
  };

  const breakpointState$ = new BehaviorSubject({
    matches: false,
    breakpoints: {
      '(max-width: 599px)': false,
      '(min-width: 600px) and (max-width: 839px)': false,
      '(min-width: 840px)': true,
    },
  });
  const mockBreakpointObserver = {
    observe: () => breakpointState$.asObservable(),
  };

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [
        AppShellComponent,
        MatSnackBarModule,
        NoopAnimationsModule,
        RouterTestingModule,
      ],
      providers: [
        { provide: ImportService, useValue: mockImportService },
        { provide: FeedsMetricsService, useValue: mockMetricsService },
        { provide: FeedsEventsService, useValue: mockEventsService },
        { provide: RegionService, useValue: mockRegionService },
        { provide: BreakpointObserver, useValue: mockBreakpointObserver },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(AppShellComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  it('refreshes data and triggers events', () => {
    component.onRefresh();

    expect(mockRegionService.clearCache).toHaveBeenCalled();
    expect(mockImportService.refreshActiveImports).toHaveBeenCalled();
    expect(mockEventsService.triggerRefresh).toHaveBeenCalled();
  });

  it('navigates when regions breadcrumb is selected', () => {
    const preventDefault = jasmine.createSpy('preventDefault');
    const stopPropagation = jasmine.createSpy('stopPropagation');

    component.onBreadcrumbSelected({
      breadcrumb: {
        id: 'regions',
        label: 'Regions',
        link: ['/regions/discover'],
      },
      originalEvent: {
        preventDefault,
        stopPropagation,
      } as unknown as MouseEvent,
    });

    expect(preventDefault).toHaveBeenCalled();
    expect(stopPropagation).toHaveBeenCalled();
    expect(mockMetricsService.resetSelectedRegion).toHaveBeenCalled();
  });

  it('ignores breadcrumb selections outside regions', () => {
    const preventDefault = jasmine.createSpy('preventDefault');
    const stopPropagation = jasmine.createSpy('stopPropagation');

    component.onBreadcrumbSelected({
      breadcrumb: { id: 'feeds', label: 'Feeds', link: ['/feeds/imports'] },
      originalEvent: {
        preventDefault,
        stopPropagation,
      } as unknown as MouseEvent,
    });

    expect(preventDefault).not.toHaveBeenCalled();
    expect(stopPropagation).not.toHaveBeenCalled();
  });

  it('toggles the sidenav only on handset layouts', async () => {
    await component.toggleSidenav();
    expect(component.sidebarOpened).toBeFalse();

    breakpointState$.next({
      matches: true,
      breakpoints: {
        '(max-width: 599px)': true,
        '(min-width: 600px) and (max-width: 839px)': false,
        '(min-width: 840px)': false,
      },
    });
    await component.toggleSidenav();
    expect(component.sidebarOpened).toBeTrue();
  });

  it('updates sidebar state on opened change', () => {
    component.onSidenavOpenedChange(true);
    expect(component.sidebarOpened).toBeTrue();
  });

  it('falls back to zero active imports when list is empty', async () => {
    mockImportService.getActiveImportsObservable = () =>
      of<FeedImportSummary[]>([]);

    const testFixture = TestBed.createComponent(AppShellComponent);
    const testComponent = testFixture.componentInstance;

    const activeCount = await firstValueFrom(testComponent.activeImportCount$);
    expect(activeCount).toBe(0);
  });
});
