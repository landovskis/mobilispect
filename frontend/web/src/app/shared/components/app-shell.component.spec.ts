import { ComponentFixture, TestBed } from '@angular/core/testing';
import { AppShellComponent } from './app-shell.component';
import { BreakpointObserver } from '@angular/cdk/layout';
import { MatSnackBarModule } from '@angular/material/snack-bar';
import { ImportService } from '../../feeds/services/import.service';
import { FeedsMetricsService } from '../../feeds/services/feeds-metrics.service';
import { FeedsEventsService } from '../../feeds/services/feeds-events.service';
import { RegionService } from '../../feeds/services/region.service';
import { BehaviorSubject, firstValueFrom, of } from 'rxjs';
import { FeedImportSummary } from '../../feeds/models';
import { RouterTestingModule } from '@angular/router/testing';
import { NoopAnimationsModule } from '@angular/platform-browser/animations';
import { vi } from 'vitest';

describe('AppShellComponent', () => {
  let component: AppShellComponent;
  let fixture: ComponentFixture<AppShellComponent>;

  let activeImports$ = of<FeedImportSummary[] | null>([]);
  const mockImportService = {
    getActiveImportsObservable: () => activeImports$,
    refreshActiveImports: vi.fn(),
  };

  const mockMetricsService = {
    discoverFeedCount$: of(0),
    totalImportElements$: of(0),
    resetSelectedRegion: vi.fn(),
  };

  const mockEventsService = {
    triggerRefresh: vi.fn(),
  };

  const mockRegionService = {
    clearCache: vi.fn(),
  };

  const breakpointState$ = new BehaviorSubject({ matches: false });
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
    const preventDefault = vi.fn();
    const stopPropagation = vi.fn();

    component.onBreadcrumbSelected({
      breadcrumb: {
        id: 'regions',
        label: 'Regions',
        link: ['/regions'],
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
    const preventDefault = vi.fn();
    const stopPropagation = vi.fn();

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
    expect(component.sidebarOpened).toBe(false);

    breakpointState$.next({ matches: true });
    await component.toggleSidenav();
    expect(component.sidebarOpened).toBe(true);
  });

  it('updates sidebar state on opened change', () => {
    component.onSidenavOpenedChange(true);
    expect(component.sidebarOpened).toBe(true);
  });

  it('falls back to zero active imports when data is missing', async () => {
    activeImports$ = of(null);

    const testFixture = TestBed.createComponent(AppShellComponent);
    const testComponent = testFixture.componentInstance;

    const activeCount = await firstValueFrom(testComponent.activeImportCount$);
    expect(activeCount).toBe(0);
  });
});
