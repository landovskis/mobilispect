import { Routes } from '@angular/router';

export const routes: Routes = [
  {
    path: '',
    redirectTo: '/feeds/discover',
    pathMatch: 'full'
  },
  {
    path: 'transit-frequency',
    loadChildren: () => import('./transit-frequency/transit-frequency.module').then(m => m.TransitFrequencyModule)
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
