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
        loadChildren: () => import('./transit-frequency/transit-frequency.module').then(m => m.TransitFrequencyModule)
      },
      {
        path: 'regions',
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
    ]
  }
];
