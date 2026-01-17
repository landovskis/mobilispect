import {
  Component,
  OnInit,
  ChangeDetectionStrategy,
  inject,
} from '@angular/core';

import { Router, RouterModule } from '@angular/router';
import { RegionService } from '../../feeds/services/region.service';
import { MetropolitanRegion } from '../../feeds/models/region.models';
import { BrandCardComponent } from '../../shared/components/brand-card.component';
import { BrandButtonComponent } from '../../shared/components/brand-button.component';
import { RegionSelectorComponent } from '../components/region-selector.component';

@Component({
  selector: 'app-region-list',
  standalone: true,
  imports: [
    RouterModule,
    BrandCardComponent,
    BrandButtonComponent,
    RegionSelectorComponent,
  ],
  template: `
    <app-brand-card title="Regions" subtitle="All regions with imported feeds">
      <app-region-selector
        [regions]="regions"
        [selectedRegionId]="selectedRegionId"
        (regionChange)="onRegionChange($event)"
      >
      </app-region-selector>
      @if (selectedRegionId) {
        <div class="actions my-3">
          <app-brand-button variant="ghost" size="sm" (click)="clearSelection()"
            >Show all regions</app-brand-button
          >
        </div>
      }
      <div class="grid flex flex-col gap-3" role="list">
        @for (region of filteredRegions; track region.regionOnestopId) {
          <div
            class="region-card flex items-center justify-between gap-4 rounded-xl border border-[var(--mat-sys-outline,#e2e8f0)] p-3"
            role="listitem"
            tabindex="0"
            (click)="goToRegion(region.regionOnestopId)"
            (keydown.enter)="goToRegion(region.regionOnestopId)"
            (keydown.space)="goToRegion(region.regionOnestopId)"
          >
            <div
              class="info flex flex-col gap-0.5 text-[var(--mat-sys-on-surface,#0f172a)]"
            >
              <div class="name">{{ region.name }}</div>
              <small>{{ region.adm0Name }} {{ region.adm1Name }}</small>
              <small>Feeds: {{ region.feedCount }}</small>
            </div>
            <a
              class="btn-link no-underline"
              [routerLink]="['/regions', region.regionOnestopId]"
              (click)="$event.stopPropagation()"
            >
              <app-brand-button variant="primary"> View </app-brand-button>
            </a>
          </div>
        }
      </div>
    </app-brand-card>
  `,
  styles: [
    `
      .name {
        font-weight: 700;
      }
      :host-context(.dark-theme) .region-card {
        border-color: rgba(148, 163, 184, 0.3);
      }
      :host-context(.dark-theme) .info {
        color: var(--mat-sys-on-surface, #e5e7eb);
      }
    `,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class RegionListComponent implements OnInit {
  regions: MetropolitanRegion[] = [];
  filteredRegions: MetropolitanRegion[] = [];
  selectedRegionId: string | null = null;

  private readonly regionService = inject(RegionService);
  private readonly router = inject(Router);

  constructor() {}

  ngOnInit(): void {
    this.regionService.listRegions().subscribe((regions) => {
      this.regions = [...regions].sort((a, b) => a.name.localeCompare(b.name));
      this.filteredRegions = this.regions;
    });
  }

  goToRegion(regionId: string): void {
    this.router.navigate(['/regions', regionId]);
  }

  onRegionChange(regionId: string): void {
    this.selectedRegionId = regionId;
    this.filteredRegions = this.regions.filter(
      (r) => r.regionOnestopId === regionId,
    );
  }

  clearSelection(): void {
    this.selectedRegionId = null;
    this.filteredRegions = this.regions;
  }
}
