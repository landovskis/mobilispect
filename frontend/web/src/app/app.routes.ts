import { Routes } from '@angular/router';
import { AgencyBreadcrumbResolver } from './agencies/resolvers/agency-breadcrumb.resolver';
import { AppShellComponent } from './shared/components/app-shell.component';
import { RouteBreadcrumbResolver } from './routes/resolvers/route-breadcrumb.resolver';

export const routes: Routes = [
  {
    path: '',
    component: AppShellComponent,
    children: [
      {
        path: '',
        redirectTo: '/regions',
        pathMatch: 'full',
      },
      {
        path: 'regions',
        loadChildren: () =>
          import('./routes/routes.routes').then(
            (m) => m.ROUTES_ROUTES,
          ),
        data: {
          breadcrumb: 'Regions',
        },
      },

      {
        path: 'feeds',
        loadChildren: () =>
          import('./feeds/feed-management.routes').then(
            (m) => m.FEED_MANAGEMENT_ROUTES,
          ),
        data: {
          title: 'Feeds',
          breadcrumb: 'Feeds',
        },
      },
      {
        path: 'agencies',
        data: { breadcrumb: 'Agencies' },
        children: [
          {
            path: ':agencyId',
            loadComponent: () =>
              import('./agencies/pages/agency-page.component').then(
                (m) => m.AgencyPageComponent,
              ),
            resolve: {
              breadcrumb: AgencyBreadcrumbResolver,
            },
          },
        ],
      },
      {
        path: 'routes/:routeId',
        loadComponent: () =>
          import('./routes/pages/route-detail/route-detail-page.component').then(
            (m) => m.RouteDetailPageComponent,
          ),
        resolve: {
          breadcrumb: RouteBreadcrumbResolver,
        },
      },
      {
        path: '**',
        redirectTo: '/regions',
      },
    ],
  },
];
