import { Routes } from '@angular/router';
import { FeedImportsPageComponent } from './pages/feed-imports.page';

export const FEED_MANAGEMENT_ROUTES: Routes = [
  {
    path: '',
    redirectTo: '/regions/discover',
    pathMatch: 'full',
  },
  {
    path: '',
    data: {},
    children: [
      {
        path: 'discover/:regionId',
        redirectTo: '/regions/discover/:regionId',
        pathMatch: 'full',
      },
      {
        path: 'discover',
        redirectTo: '/regions/discover',
        pathMatch: 'full',
      },
      {
        path: 'regions',
        redirectTo: 'discover',
        pathMatch: 'full',
      },
      {
        path: 'imports',
        component: FeedImportsPageComponent,
        data: {
          title: 'Imports',
          breadcrumb: 'Imports',
          permissions: ['FEED_VIEWER', 'FEED_OPERATOR', 'FEED_MANAGER'],
        },
      },
      {
        path: 'history',
        redirectTo: 'imports',
        pathMatch: 'full',
      },
      {
        path: 'import/:importId',
        component: FeedImportsPageComponent,
        data: {
          title: 'Import Details',
          breadcrumb: 'Import Details',
          permissions: ['FEED_VIEWER', 'FEED_OPERATOR', 'FEED_MANAGER'],
        },
      },
    ],
  },
];
