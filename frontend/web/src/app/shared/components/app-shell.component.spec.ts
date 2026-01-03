import { ComponentFixture, TestBed } from '@angular/core/testing';
import { AppShellComponent } from './app-shell.component';
import { MatSnackBarModule } from '@angular/material/snack-bar';
import { ImportService } from '../../feeds/services/import.service';
import { FeedsMetricsService } from '../../feeds/services/feeds-metrics.service';
import { FeedsEventsService } from '../../feeds/services/feeds-events.service';
import { RegionService } from '../../feeds/services/region.service';
import { firstValueFrom, of } from 'rxjs';
import { Router } from '@angular/router';
import { RouterTestingModule } from '@angular/router/testing';
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


    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [
                AppShellComponent,
                MatSnackBarModule,
                NoopAnimationsModule,
                RouterTestingModule
            ],
            providers: [
                { provide: ImportService, useValue: mockImportService },
                { provide: FeedsMetricsService, useValue: mockMetricsService },
                { provide: FeedsEventsService, useValue: mockEventsService },
                { provide: RegionService, useValue: mockRegionService }
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
            breadcrumb: { id: 'regions', label: 'Regions', link: ['/regions/discover'] },
            originalEvent: { preventDefault, stopPropagation } as unknown as MouseEvent
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
            originalEvent: { preventDefault, stopPropagation } as unknown as MouseEvent
        });

        expect(preventDefault).not.toHaveBeenCalled();
        expect(stopPropagation).not.toHaveBeenCalled();
    });

    it('falls back to zero active imports when data is missing', async () => {
        mockImportService.getActiveImportsObservable = () => of(null as any);

        const testFixture = TestBed.createComponent(AppShellComponent);
        const testComponent = testFixture.componentInstance;

        const activeCount = await firstValueFrom(testComponent.activeImportCount$);
        expect(activeCount).toBe(0);
    });
});
