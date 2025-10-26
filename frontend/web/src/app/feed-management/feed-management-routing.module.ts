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
          title: 'Regional Transit Feeds',
          breadcrumb: 'Regions',
          view: 'regions'
        }
      },
      {
        path: 'imports',
        component: FeedManagementComponent,
        data: {
          title: 'Active Imports',
          breadcrumb: 'Active Imports',
          view: 'imports'
        }
      },
      {
        path: 'history',
        component: FeedManagementComponent,
        data: {
          title: 'Import History',
          breadcrumb: 'Import History',
          view: 'history'
        }
      },
      {
        path: 'region/:regionId',
        component: FeedManagementComponent,
        data: {
          title: 'Region Details',
          breadcrumb: 'Region Details',
          view: 'regions'
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
      }
    ]
  }
];

@NgModule({
  imports: [RouterModule.forChild(routes)],
  exports: [RouterModule]
})
export class FeedManagementRoutingModule { }
