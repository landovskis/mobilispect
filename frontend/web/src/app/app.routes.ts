import { Routes } from '@angular/router';

export const routes: Routes = [
  {
    path: '',
    redirectTo: '/feed-management',
    pathMatch: 'full'
  },
  {
    path: 'feed-management',
    loadChildren: () => import('./feed-management/feed-management.module').then(m => m.FeedManagementModule),
    data: {
      title: 'Feed Management',
      breadcrumb: 'Feed Management'
    }
  },
  {
    path: '**',
    redirectTo: '/feed-management'
  }
];
