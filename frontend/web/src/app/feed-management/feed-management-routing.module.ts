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
    }
  },
  {
    path: 'imports',
    component: FeedManagementComponent,
    canActivate: [FeedManagementGuard],
    data: {
      title: 'Import History',
      breadcrumb: 'Import History',
      permissions: ['FEED_VIEWER', 'FEED_OPERATOR', 'FEED_MANAGER']
    }
  },
  {
    path: 'history',
    component: FeedManagementComponent,
    canActivate: [FeedManagementGuard],
    data: {
      title: 'Import History',
      breadcrumb: 'Import History',
      permissions: ['FEED_VIEWER', 'FEED_OPERATOR', 'FEED_MANAGER']
    }
  },
  {
    path: 'import/:importId',
    component: FeedManagementComponent,
    canActivate: [FeedManagementGuard],
    data: {
      title: 'Import Details',
      breadcrumb: 'Import Details',
      permissions: ['FEED_VIEWER', 'FEED_OPERATOR', 'FEED_MANAGER']
    }
  }
];

@NgModule({
  imports: [RouterModule.forChild(routes)],
  exports: [RouterModule]
})
export class FeedManagementRoutingModule { }
