import { Routes } from '@angular/router';
import { RegionListComponent } from '../regions/pages/region-list.component';
import { RegionDetailComponent } from '../regions/pages/region-detail.component';
import { RegionBreadcrumbResolver } from '../regions/resolvers/region-breadcrumb.resolver';
import { RouteBreadcrumbResolver } from './resolvers/route-breadcrumb.resolver';
import { DiscoverRegionsPageComponent } from '../regions/pages/discover-regions.page';

export const TRANSIT_FREQUENCY_ROUTES: Routes = [
  {
    path: '',
    component: RegionListComponent,
    data: {}
  },
  {
    path: 'discover/:regionId',
    component: DiscoverRegionsPageComponent,
    resolve: {
      breadcrumb: RegionBreadcrumbResolver
    },
    data: {
      title: 'Discover Regions',
      permissions: ['FEED_VIEWER', 'FEED_OPERATOR', 'FEED_MANAGER']
    }
  },
  {
    path: 'discover',
    component: DiscoverRegionsPageComponent,
    data: {
      title: 'Discover Regions',
      breadcrumb: 'Discover Regions',
      permissions: ['FEED_VIEWER', 'FEED_OPERATOR', 'FEED_MANAGER']
    }
  },
  {
    path: ':regionId',
    component: RegionDetailComponent,
    resolve: {
      breadcrumb: RegionBreadcrumbResolver
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
