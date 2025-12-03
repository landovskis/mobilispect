import { NgModule } from '@angular/core';
import { RouterModule, Routes } from '@angular/router';
import { RegionListComponent } from './pages/region-list/region-list.component';
import { RegionDetailComponent } from './pages/region-detail/region-detail.component';

const routes: Routes = [
  {
    path: '',
    component: RegionListComponent
  },
  {
    path: ':regionId',
    component: RegionDetailComponent
  },
  {
    path: 'routes/:routeId',
    loadComponent: () => import('./pages/route-frequency/route-frequency.component').then(m => m.RouteFrequencyComponent)
  }
];

@NgModule({
  imports: [RouterModule.forChild(routes)],
  exports: [RouterModule]
})
export class TransitFrequencyModule {}
