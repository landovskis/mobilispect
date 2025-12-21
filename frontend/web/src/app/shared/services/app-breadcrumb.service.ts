import { Injectable } from '@angular/core';
import { ActivatedRouteSnapshot, Params } from '@angular/router';

export interface Breadcrumb {
    id?: string;
    label: string;
    link?: string | any[];
    queryParams?: Params | null;
}

@Injectable({
    providedIn: 'root'
})
export class AppBreadcrumbService {

    getBreadcrumbs(route: ActivatedRouteSnapshot): Breadcrumb[] {
        return this.buildBreadcrumbsFromRoute(route);
    }

    private buildBreadcrumbsFromRoute(
        route: ActivatedRouteSnapshot,
        url: string = '',
        crumbs: Breadcrumb[] = []
    ): Breadcrumb[] {
        const children = route.children.filter(child => child.outlet === 'primary');

        if (!children.length) {
            return crumbs;
        }

        const [child] = children;
        if (!child) return crumbs;

        const routeURL = child.url.map(segment => segment.path).join('/');
        const nextUrl = routeURL ? `${url}/${routeURL}` : url;
        const label = child.data['breadcrumb'];

        const lastCrumb = crumbs[crumbs.length - 1];
        if (label && lastCrumb?.label !== label) {
            crumbs.push({
                id: child.routeConfig?.path ?? label,
                label,
                link: [nextUrl || '/']
            });
        }

        return this.buildBreadcrumbsFromRoute(child, nextUrl, crumbs);
    }
}
