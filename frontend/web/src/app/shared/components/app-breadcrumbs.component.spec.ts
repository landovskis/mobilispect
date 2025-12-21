import { ComponentFixture, TestBed } from '@angular/core/testing';
import { AppBreadcrumbsComponent } from './app-breadcrumbs.component';
import { Router, NavigationEnd, RouterModule } from '@angular/router';
import { AppBreadcrumbService } from '../services/app-breadcrumb.service';
import { By } from '@angular/platform-browser';

describe('AppBreadcrumbsComponent', () => {
    let fixture: ComponentFixture<AppBreadcrumbsComponent>;
    let component: AppBreadcrumbsComponent;
    let breadcrumbService: jasmine.SpyObj<AppBreadcrumbService>;
    let router: Router;

    beforeEach(async () => {
        const breadcrumbServiceSpy = jasmine.createSpyObj('AppBreadcrumbService', ['getBreadcrumbs']);

        await TestBed.configureTestingModule({
            imports: [AppBreadcrumbsComponent, RouterModule],
            providers: [
                { provide: AppBreadcrumbService, useValue: breadcrumbServiceSpy }
            ]
        }).compileComponents();

        fixture = TestBed.createComponent(AppBreadcrumbsComponent);
        component = fixture.componentInstance;
        breadcrumbService = TestBed.inject(AppBreadcrumbService) as jasmine.SpyObj<AppBreadcrumbService>;
        router = TestBed.inject(Router);
    });

    it('should create', () => {
        expect(component).toBeTruthy();
    });

    it('should load breadcrumbs from service on init', () => {
        const mockCrumbs = [{ id: 'test', label: 'Test', link: ['/'] }];
        breadcrumbService.getBreadcrumbs.and.returnValue(mockCrumbs);

        fixture.detectChanges(); // ngOnInit

        expect(component.breadcrumbs).toEqual(mockCrumbs);
        expect(breadcrumbService.getBreadcrumbs).toHaveBeenCalled();
    });

    it('should update breadcrumbs on navigation', () => {
        breadcrumbService.getBreadcrumbs.and.returnValue([]);
        fixture.detectChanges();

        const mockCrumbs = [{ id: 'new', label: 'New', link: ['/new'] }];
        breadcrumbService.getBreadcrumbs.and.returnValue(mockCrumbs);

        const navEnd = new NavigationEnd(1, '/new', '/new');
        (router.events as any).next(navEnd);

        expect(component.breadcrumbs).toEqual(mockCrumbs);
    });
});
