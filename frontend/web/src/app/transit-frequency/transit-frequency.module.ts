import { NgModule } from '@angular/core';
import { RouterModule, Routes } from '@angular/router';
import { RegionListComponent } from './pages/region-list/region-list.component';

const routes: Routes = [
  {
    path: 'regions',
    component: RegionListComponent
  },
  {
    path: 'regions/:regionId',
    component: RegionListComponent
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
