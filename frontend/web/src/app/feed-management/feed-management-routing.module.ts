import { NgModule } from '@angular/core';
import { RouterModule, Routes } from '@angular/router';
import { FeedManagementComponent } from './pages/feed-management.component';
import { FeedManagementGuard } from './feed-management.guard';

const routes: Routes = [
  {
    path: '',
    component: FeedManagementComponent,
    canActivate: [FeedManagementGuard],
    data: {
      title: 'Feed Management',
      breadcrumb: 'Feed Management',
      permissions: ['FEED_VIEWER', 'FEED_OPERATOR', 'FEED_MANAGER']
    },
    children: [
      {
        path: '',
        redirectTo: 'regions',
        pathMatch: 'full'
      },
      {
        path: 'regions',
        component: FeedManagementComponent,
        data: {
          title: 'Feed Management',
          breadcrumb: 'Feed Management',
          view: 'regions'
        }
      },
      {
        path: 'imports',
        component: FeedManagementComponent,
        data: {
          title: 'Active Imports',
          breadcrumb: 'Active Imports',
          view: 'imports',
          tab: 'active'
        }
      },
      {
        path: 'history',
        component: FeedManagementComponent,
        data: {
          title: 'Import History',
          breadcrumb: 'Import History',
          view: 'imports',
          tab: 'history'
        }
      },
      {
        path: 'import/:importId',
        component: FeedManagementComponent,
        data: {
          title: 'Import Details',
          breadcrumb: 'Import Details',
          view: 'imports'
        }
      },
      {
        path: ':onestopId',
        component: FeedManagementComponent,
        data: {
          title: 'Region Feeds',
          breadcrumb: 'Feeds',
          view: 'feeds'
        }
      }
    ]
  }
];

@NgModule({
  imports: [RouterModule.forChild(routes)],
  exports: [RouterModule]
})
export class FeedManagementRoutingModule { }
