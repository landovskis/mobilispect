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
                { provide: BreakpointObserver, useValue: mockBreakpointObserver }
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

    it('should build breadcrumbs from route data', () => {
        const root = router.routerState.snapshot.root;
        // Mock the route tree structure
        spyOnProperty(root, 'children').and.returnValue([
            {
                outlet: 'primary',
                url: [{ path: 'regions' }],
                data: { breadcrumb: 'Regions' },
                children: [],
                paramMap: { get: () => null },
                routeConfig: { path: 'regions' }
            } as any
        ]);

        // Trigger navigation event logic manually since we can't easily trigger real router events in unit test without complex setup
        // Accessing private method via any cast for testing purposes, or we could refactor to public
        const crumbs = (component as any).buildBreadcrumbsFromRoute(root);

        expect(crumbs.length).toBe(1);
        expect(crumbs[0].label).toBe('Regions');
        expect(crumbs[0].link).toEqual(['/regions']);
    });

    it('should handle nested breadcrumbs', () => {
        const root = router.routerState.snapshot.root;
        // Mock nested route tree
        const childRoute = {
            outlet: 'primary',
            url: [{ path: '123' }],
            data: { breadcrumb: 'Specific Region' },
            children: [],
            paramMap: { get: () => null },
            routeConfig: { path: ':id' }
        };

        spyOnProperty(root, 'children').and.returnValue([
            {
                outlet: 'primary',
                url: [{ path: 'regions' }],
                data: { breadcrumb: 'Regions' },
                children: [childRoute],
                paramMap: { get: () => null },
                routeConfig: { path: 'regions' }
            } as any
        ]);

        const crumbs = (component as any).buildBreadcrumbsFromRoute(root);

        expect(crumbs.length).toBe(2);
        expect(crumbs[0].label).toBe('Regions');
        expect(crumbs[1].label).toBe('Specific Region');
        expect(crumbs[1].link).toEqual(['/regions/123']);
    });
});
