import { NgModule } from '@angular/core';
import { RouterModule, Routes } from '@angular/router';
import { RegionListComponent } from './pages/region-list/region-list.component';
import { RegionSelectComponent } from './pages/region-select/region-select.component';

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
    path: 'regions-select/imported',
    component: RegionSelectComponent
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
