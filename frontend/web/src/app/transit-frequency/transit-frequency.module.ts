import { NgModule } from '@angular/core';
import { RouterModule, Routes } from '@angular/router';
import { RegionListComponent } from '../regions/pages/region-list.component';
import { RegionDetailComponent } from '../regions/pages/region-detail.component';
import { RegionBreadcrumbResolver } from '../regions/resolvers/region-breadcrumb.resolver';
import { RouteBreadcrumbResolver } from './resolvers/route-breadcrumb.resolver';

const routes: Routes = [
  {
    path: '',
    component: RegionListComponent,
    data: {}
  },
  {
    path: ':regionId',
    component: RegionDetailComponent,
    resolve: {
      breadcrumb: RegionBreadcrumbResolver
    }
  },
  {
    path: 'routes/:routeId',
    loadComponent: () => import('./pages/route-frequency/route-frequency.component').then(m => m.RouteFrequencyComponent),
    resolve: {
      breadcrumb: RouteBreadcrumbResolver
    }
  }
];

@NgModule({
  imports: [RouterModule.forChild(routes)],
  exports: [RouterModule]
})
export class TransitFrequencyModule { }
