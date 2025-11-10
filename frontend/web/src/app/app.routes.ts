import { Routes } from '@angular/router';

export const routes: Routes = [
  {
    path: '',
    redirectTo: '/feeds/discover',
    pathMatch: 'full'
  },
  {
    path: 'feeds',
    loadChildren: () => import('./feeds/feed-management.module').then(m => m.FeedManagementModule),
    data: {
      title: 'Feed Management',
      breadcrumb: 'Feed Management'
    }
  },
  {
    path: '**',
    redirectTo: '/feeds/discover'
  }
];
