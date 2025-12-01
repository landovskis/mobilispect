import { NgModule } from '@angular/core';
import { RouterModule, Routes } from '@angular/router';
import { DiscoverFeedsPageComponent } from './pages/discover-feeds.page';
import { FeedImportsPageComponent } from './pages/feed-imports.page';

const routes: Routes = [
  {
    path: '',
    redirectTo: 'discover',
    pathMatch: 'full'
  },
  {
    path: '',
    children: [
      {
        path: 'discover',
        component: DiscoverFeedsPageComponent,
        data: {
          title: 'Discover Feeds',
          breadcrumb: 'Discover',
          permissions: ['FEED_VIEWER', 'FEED_OPERATOR', 'FEED_MANAGER']
        }
      },
      {
        path: 'regions',
        redirectTo: 'discover',
        pathMatch: 'full'
      },
      {
        path: 'imports',
        component: FeedImportsPageComponent,
        data: {
          title: 'Imports',
          breadcrumb: 'Imports',
          permissions: ['FEED_VIEWER', 'FEED_OPERATOR', 'FEED_MANAGER']
        }
      },
      {
        path: 'history',
        redirectTo: 'imports',
        pathMatch: 'full'
      },
      {
        path: 'import/:importId',
        component: FeedImportsPageComponent,
        data: {
          title: 'Import Details',
          breadcrumb: 'Import Details',
          permissions: ['FEED_VIEWER', 'FEED_OPERATOR', 'FEED_MANAGER']
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
