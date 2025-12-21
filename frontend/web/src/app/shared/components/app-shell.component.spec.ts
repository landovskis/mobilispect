import { ComponentFixture, TestBed } from '@angular/core/testing';
import { AppShellComponent } from './app-shell.component';
import { BreakpointObserver } from '@angular/cdk/layout';
import { MatSnackBarModule } from '@angular/material/snack-bar';
import { ImportService } from '../../feeds/services/import.service';
import { FeedsMetricsService } from '../../feeds/services/feeds-metrics.service';
import { FeedsEventsService } from '../../feeds/services/feeds-events.service';
import { RegionService } from '../../feeds/services/region.service';
import { of } from 'rxjs';
import { ActivatedRoute, NavigationEnd, Router, RouterModule } from '@angular/router';
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

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [
    AppShellComponent,
    MatSnackBarModule,
    NoopAnimationsModule,
    RouterModule
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
});
