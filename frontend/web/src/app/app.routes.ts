import { Routes } from '@angular/router';
import { AppShellComponent } from './shared/components/app-shell.component';

export const routes: Routes = [
  {
    path: '',
    component: AppShellComponent,
    children: [
      {
        path: '',
        redirectTo: '/feeds/discover',
        pathMatch: 'full'
      },
      {
        path: 'regions',
        loadChildren: () => import('./transit-frequency/transit-frequency.module').then(m => m.TransitFrequencyModule),
        data: {
          breadcrumb: 'Regions'
        }
      },
      {
        path: 'regions',
        loadChildren: () => import('./transit-frequency/transit-frequency.module').then(m => m.TransitFrequencyModule),
        data: {
          breadcrumb: 'Regions'
        }
      },
      {
        path: 'feeds',
        loadChildren: () => import('./feeds/feed-management.module').then(m => m.FeedManagementModule),
        data: {
          title: 'Feeds',
          breadcrumb: 'Feeds'
        }
      },
      {
        path: 'agencies/:agencyId',
        loadComponent: () => import('./agencies/pages/agency-page.component').then(m => m.AgencyPageComponent),
        resolve: {
          breadcrumb: (await import('./agencies/resolvers/agency-breadcrumb.resolver')).AgencyBreadcrumbResolver
        }
      },
      {
        path: 'routes/:routeId',
        loadComponent: () => import('./transit-frequency/pages/route-detail/route-detail-page.component').then(m => m.RouteDetailPageComponent),
        data: {
          breadcrumb: 'Route Details'
        }
      },
      {
        path: '**',
        redirectTo: '/feeds/discover'
      }
    ]
  }
];
