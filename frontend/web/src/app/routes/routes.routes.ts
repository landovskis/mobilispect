import { Routes } from '@angular/router';
import { RegionsPageComponent } from '../regions/pages/regions.page';
import { RegionBreadcrumbResolver } from '../regions/resolvers/region-breadcrumb.resolver';
import { RouteBreadcrumbResolver } from './resolvers/route-breadcrumb.resolver';

export const TRANSIT_FREQUENCY_ROUTES: Routes = [
  {
    path: '',
    component: RegionsPageComponent,
    data: {},
  },
  {
    path: 'discover/:regionId',
    redirectTo: '/regions/:regionId',
    pathMatch: 'full',
    data: {
      title: 'Regions',
      permissions: ['FEED_VIEWER', 'FEED_OPERATOR', 'FEED_MANAGER'],
    },
  },
  {
    path: 'discover',
    redirectTo: '/regions',
    pathMatch: 'full',
    data: {
      title: 'Regions',
      permissions: ['FEED_VIEWER', 'FEED_OPERATOR', 'FEED_MANAGER'],
    },
  },
  {
    path: ':regionId',
    component: RegionsPageComponent,
    resolve: {
      breadcrumb: RegionBreadcrumbResolver,
    },
  },
  {
    path: 'routes/:routeId',
    loadComponent: () =>
      import('./pages/route-frequency/route-frequency.component').then(
        (m) => m.RouteFrequencyComponent,
      ),
    resolve: {
      breadcrumb: RouteBreadcrumbResolver,
    },
  },
];
