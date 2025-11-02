import { Routes } from '@angular/router';

export const routes: Routes = [
  {
    path: '',
    redirectTo: '/feeds',
    pathMatch: 'full'
  },
  {
    path: 'feeds',
    loadChildren: () => import('./feed-management/feed-management.module').then(m => m.FeedManagementModule),
    data: {
      title: 'Feed Management',
      breadcrumb: 'Feed Management'
    }
  },
  {
    path: '**',
    redirectTo: '/feeds'
  }
];
