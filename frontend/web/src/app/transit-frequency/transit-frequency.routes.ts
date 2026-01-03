import { Routes } from '@angular/router';
import { RegionsPageComponent } from '../regions/pages/regions.page';
import { RegionBreadcrumbResolver } from '../regions/resolvers/region-breadcrumb.resolver';
import { RouteBreadcrumbResolver } from './resolvers/route-breadcrumb.resolver';

export const TRANSIT_FREQUENCY_ROUTES: Routes = [
  {
    path: '',
    component: RegionsPageComponent,
    data: {
      title: 'Regions',
      breadcrumb: 'Regions',
      permissions: ['FEED_VIEWER', 'FEED_OPERATOR', 'FEED_MANAGER']
    }
  },
  {
    path: 'discover/:regionId',
    redirectTo: ':regionId',
    pathMatch: 'full'
  },
  {
    path: 'discover',
    redirectTo: '',
    pathMatch: 'full'
  },
  {
    path: ':regionId',
    component: RegionsPageComponent,
    resolve: {
      breadcrumb: RegionBreadcrumbResolver
    },
    data: {
      title: 'Region Details',
      permissions: ['FEED_VIEWER', 'FEED_OPERATOR', 'FEED_MANAGER']
    }
  },
  {
    path: 'routes/:routeId',
    loadComponent: () => import('./pages/route-frequency/route-frequency.component').then(m => m.RouteFrequencyComponent),
    resolve: {
      breadcrumb: RouteBreadcrumbResolver
    }
  }
];
