import { NgModule } from '@angular/core';
import { RouterModule, Routes } from '@angular/router';
import { RegionListComponent } from '../regions/pages/region-list/region-list.component';
import { RegionDetailComponent } from '../regions/pages/region-detail/region-detail.component';

const routes: Routes = [
  {
    path: '',
    component: RegionListComponent,
    data: {
      breadcrumb: 'Regions'
    }
  },
  {
    path: ':regionId',
    component: RegionDetailComponent,
    data: {
      breadcrumb: 'Region'
    }
  },
  {
    path: 'routes/:routeId',
    loadComponent: () => import('./pages/route-frequency/route-frequency.component').then(m => m.RouteFrequencyComponent),
    data: {
      breadcrumb: 'Route'
    }
  }
];

@NgModule({
  imports: [RouterModule.forChild(routes)],
  exports: [RouterModule]
})
export class TransitFrequencyModule {}
