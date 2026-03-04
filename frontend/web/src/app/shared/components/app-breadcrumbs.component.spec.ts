import { ComponentFixture, TestBed } from '@angular/core/testing';
import { AppBreadcrumbsComponent } from './app-breadcrumbs.component';
import { Router, NavigationEnd } from '@angular/router';
import { RouterTestingModule } from '@angular/router/testing';
import { AppBreadcrumbService } from '../services/app-breadcrumb.service';
import { Subject } from 'rxjs';
import { vi } from 'vitest';

describe('AppBreadcrumbsComponent', () => {
  let fixture: ComponentFixture<AppBreadcrumbsComponent>;
  let component: AppBreadcrumbsComponent;
  let breadcrumbService: AppBreadcrumbService;
  let router: Router;

  beforeEach(async () => {
    const breadcrumbServiceSpy = {
      getBreadcrumbs: vi.fn(),
    } as unknown as AppBreadcrumbService;

    await TestBed.configureTestingModule({
      imports: [AppBreadcrumbsComponent, RouterTestingModule],
      providers: [
        { provide: AppBreadcrumbService, useValue: breadcrumbServiceSpy },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(AppBreadcrumbsComponent);
    component = fixture.componentInstance;
    breadcrumbService = TestBed.inject(AppBreadcrumbService);
    router = TestBed.inject(Router);
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  it('should load breadcrumbs from service on init', () => {
    const mockCrumbs = [{ id: 'test', label: 'Test', link: ['/'] }];
    vi.mocked(breadcrumbService.getBreadcrumbs).mockReturnValue(mockCrumbs);

    fixture.detectChanges(); // ngOnInit

    expect(component.breadcrumbs).toEqual(mockCrumbs);
    expect(breadcrumbService.getBreadcrumbs).toHaveBeenCalled();
  });

  it('should update breadcrumbs on navigation', () => {
    vi.mocked(breadcrumbService.getBreadcrumbs).mockReturnValue([]);
    fixture.detectChanges();

    const mockCrumbs = [{ id: 'new', label: 'New', link: ['/new'] }];
    vi.mocked(breadcrumbService.getBreadcrumbs).mockReturnValue(mockCrumbs);

    const navEnd = new NavigationEnd(1, '/new', '/new');
    const routerEvents = router.events as unknown as Subject<NavigationEnd>;
    routerEvents.next(navEnd);

    expect(component.breadcrumbs).toEqual(mockCrumbs);
  });
});
