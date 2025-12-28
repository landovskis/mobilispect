import { TestBed } from '@angular/core/testing';
import { AppBreadcrumbService } from './app-breadcrumb.service';
import { ActivatedRouteSnapshot } from '@angular/router';

describe('AppBreadcrumbService', () => {
    let service: AppBreadcrumbService;

    beforeEach(() => {
        TestBed.configureTestingModule({
            providers: [AppBreadcrumbService]
        });
        service = TestBed.inject(AppBreadcrumbService);
    });

    it('should be created', () => {
        expect(service).toBeTruthy();
    });

    it('should build breadcrumbs from route data', () => {
        // Mock the route tree structure
        const root = {
            children: [{
                outlet: 'primary',
                url: [{ path: 'regions' }],
                data: { breadcrumb: 'Regions' },
                children: [],
                paramMap: { get: () => null },
                routeConfig: { path: 'regions' }
            }] as any
        } as ActivatedRouteSnapshot;

        const crumbs = service.getBreadcrumbs(root);

        expect(crumbs.length).toBe(1);
        expect(crumbs[0].label).toBe('Regions');
        expect(crumbs[0].link).toEqual(['/regions']);
    });

    it('should handle nested breadcrumbs', () => {
        // Mock nested route tree
        const childRoute = {
            outlet: 'primary',
            url: [{ path: '123' }],
            data: { breadcrumb: 'Specific Region' },
            children: [],
            paramMap: { get: () => null },
            routeConfig: { path: ':id' }
        };

        const root = {
            children: [{
                outlet: 'primary',
                url: [{ path: 'regions' }],
                data: { breadcrumb: 'Regions' },
                children: [childRoute],
                paramMap: { get: () => null },
                routeConfig: { path: 'regions' }
            }] as any
        } as ActivatedRouteSnapshot;

        const crumbs = service.getBreadcrumbs(root);

        expect(crumbs.length).toBe(2);
        expect(crumbs[0].label).toBe('Regions');
        expect(crumbs[1].label).toBe('Specific Region');
        expect(crumbs[1].link).toEqual(['/regions/123']);
    });

    it('should handle empty path intermediate routes correctly', () => {
        // Mock structure: feeds -> empty -> discover
        const discoverRoute = {
            outlet: 'primary',
            url: [{ path: 'discover' }],
            data: { breadcrumb: 'Discover' },
            children: [],
            paramMap: { get: () => null },
            routeConfig: { path: 'discover' }
        };

        const emptyRoute = {
            outlet: 'primary',
            url: [], // empty path
            data: {}, // no breadcrumb
            children: [discoverRoute],
            paramMap: { get: () => null },
            routeConfig: { path: '' }
        };

        const root = {
            children: [{
                outlet: 'primary',
                url: [{ path: 'regions' }],
                data: { breadcrumb: 'Regions' },
                children: [emptyRoute],
                paramMap: { get: () => null },
                routeConfig: { path: 'regions' }
            }] as any
        } as ActivatedRouteSnapshot;

        const crumbs = service.getBreadcrumbs(root);

        expect(crumbs.length).toBe(2);
        expect(crumbs[0].label).toBe('Regions');
        expect(crumbs[0].link).toEqual(['/regions']);
        expect(crumbs[1].label).toBe('Discover');
        expect(crumbs[1].link).toEqual(['/regions/discover']);
    });

    it('skips routes without breadcrumb labels', () => {
        const root = {
            children: [{
                outlet: 'primary',
                url: [{ path: 'feeds' }],
                data: {},
                children: [],
                paramMap: { get: () => null },
                routeConfig: { path: 'feeds' }
            }] as any
        } as ActivatedRouteSnapshot;

        const crumbs = service.getBreadcrumbs(root);

        expect(crumbs.length).toBe(0);
    });
});
