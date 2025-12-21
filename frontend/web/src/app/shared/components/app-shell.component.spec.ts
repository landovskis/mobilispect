import { ComponentFixture, TestBed } from '@angular/core/testing';
import { AppShellComponent } from './app-shell.component';
import { RouterTestingModule } from '@angular/router/testing';
import { BreakpointObserver } from '@angular/cdk/layout';
import { MatSnackBarModule } from '@angular/material/snack-bar';
import { ImportService } from '../../feeds/services/import.service';
import { FeedsMetricsService } from '../../feeds/services/feeds-metrics.service';
import { FeedsEventsService } from '../../feeds/services/feeds-events.service';
import { RegionService } from '../../feeds/services/region.service';
import { of } from 'rxjs';
import { ActivatedRoute, NavigationEnd, Router } from '@angular/router';
import { NoopAnimationsModule } from '@angular/platform-browser/animations';
import { AppBreadcrumbService } from '../services/app-breadcrumb.service';

describe('AppShellComponent', () => {
    let component: AppShellComponent;
    let fixture: ComponentFixture<AppShellComponent>;
    let router: Router;

    const mockImportService = {
        getActiveImportsObservable: () => of([]),
        refreshActiveImports: jasmine.createSpy('refreshActiveImports')
    };

    const mockMetricsService = {
        discoverFeedCount$: of(0),
        totalImportElements$: of(0),
        resetSelectedRegion: jasmine.createSpy('resetSelectedRegion')
    };

    const mockEventsService = {
        triggerRefresh: jasmine.createSpy('triggerRefresh')
    };

    const mockRegionService = {
        clearCache: jasmine.createSpy('clearCache')
    };

    const mockBreakpointObserver = {
        observe: () => of({ matches: false })
    };

    const mockBreadcrumbService = {
        getBreadcrumbs: jasmine.createSpy('getBreadcrumbs').and.returnValue([])
    };

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [
                AppShellComponent,
                RouterTestingModule,
                MatSnackBarModule,
                NoopAnimationsModule
            ],
            providers: [
                { provide: ImportService, useValue: mockImportService },
                { provide: FeedsMetricsService, useValue: mockMetricsService },
                { provide: FeedsEventsService, useValue: mockEventsService },
                { provide: RegionService, useValue: mockRegionService },
                { provide: BreakpointObserver, useValue: mockBreakpointObserver },
                { provide: AppBreadcrumbService, useValue: mockBreadcrumbService }
            ]
        }).compileComponents();

        fixture = TestBed.createComponent(AppShellComponent);
        component = fixture.componentInstance;
        router = TestBed.inject(Router);
        fixture.detectChanges();
    });

    it('should create', () => {
        expect(component).toBeTruthy();
    });

    it('should default to Feeds breadcrumb', () => {
        expect(component.breadcrumbs).toEqual([
            { id: 'feeds', label: 'Feeds', link: ['/feeds/discover'] }
        ]);
    });

    it('should build breadcrumbs using service on navigation', () => {
        const mockCrumbs = [{ id: 'test', label: 'Test', link: ['/test'] }];
        (component as any).breadcrumbService.getBreadcrumbs.and.returnValue(mockCrumbs);

        // Simulate router event
        const navEnd = new NavigationEnd(1, '/test', '/test');
        (router.events as any).next(navEnd);

        expect(component.breadcrumbs).toEqual(mockCrumbs);
    });
});
