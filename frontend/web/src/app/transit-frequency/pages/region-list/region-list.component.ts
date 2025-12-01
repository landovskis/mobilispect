import { Component, OnInit, ChangeDetectionStrategy } from '@angular/core';
import { CommonModule } from '@angular/common';
import { Router, RouterModule } from '@angular/router';
import { RegionService } from '../../../feeds/services/region.service';
import { MetropolitanRegion } from '../../../feeds/models/region.models';
import { BrandCardComponent } from '../../../shared/components/brand-card.component';
import { BrandButtonComponent } from '../../../shared/components/brand-button.component';
import { RegionSelectorComponent } from '../../../feeds/components/region-selector.component';

@Component({
  selector: 'app-region-list',
  standalone: true,
  imports: [CommonModule, RouterModule, BrandCardComponent, BrandButtonComponent, RegionSelectorComponent],
  template: `
    <app-brand-card title="Regions" subtitle="All regions with imported feeds">
      <app-region-selector
        [regions]="regions"
        [selectedRegionId]="selectedRegionId"
        (regionChange)="onRegionChange($event)">
      </app-region-selector>
      <div class="actions" *ngIf="selectedRegionId">
        <app-brand-button variant="ghost" size="sm" (click)="clearSelection()">Show all regions</app-brand-button>
      </div>
      <div class="grid" role="list">
        @for (region of filteredRegions; track region.regionOnestopId) {
          <div class="region-card" role="listitem" tabindex="0" (keydown.enter)="goToRegion(region.regionOnestopId)" (keydown.space)="goToRegion(region.regionOnestopId)">
            <div class="info">
              <div class="name">{{ region.name }}</div>
              <small>{{ region.adm0Name }} {{ region.adm1Name }}</small>
              <small>Feeds: {{ region.feedCount }}</small>
            </div>
            <a class="btn-link" [routerLink]="['/regions', region.regionOnestopId]" (click)="$event.stopPropagation()">
              <app-brand-button variant="primary">
                View
              </app-brand-button>
            </a>
          </div>
        }
      </div>
    </app-brand-card>
  `,
  styles: [`
    .actions { margin: 12px 0; }
    .grid { display: flex; flex-direction: column; gap: 12px; }
    .region-card { display: flex; justify-content: space-between; align-items: center; padding: 12px; border: 1px solid var(--mat-sys-outline, #e2e8f0); border-radius: 12px; }
    .info { display: flex; flex-direction: column; gap: 2px; color: var(--mat-sys-on-surface, #0f172a); }
    .name { font-weight: 700; }
    .btn-link { text-decoration: none; }
    :host-context(.dark-theme) .region-card { border-color: rgba(148, 163, 184, 0.3); }
    :host-context(.dark-theme) .info { color: var(--mat-sys-on-surface, #e5e7eb); }
  `],
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class RegionListComponent implements OnInit {
  regions: MetropolitanRegion[] = [];
  filteredRegions: MetropolitanRegion[] = [];
  selectedRegionId: string | null = null;

  constructor(
    private readonly regionService: RegionService,
    private readonly router: Router
  ) {}

  ngOnInit(): void {
    this.regionService.listRegions().subscribe(regions => {
      this.regions = [...regions].sort((a, b) => a.name.localeCompare(b.name));
      this.filteredRegions = this.regions;
    });
  }

  goToRegion(regionId: string): void {
    this.router.navigate(['/regions', regionId]);
  }

  onRegionChange(regionId: string): void {
    this.selectedRegionId = regionId;
    this.filteredRegions = this.regions.filter(r => r.regionOnestopId === regionId);
  }

  clearSelection(): void {
    this.selectedRegionId = null;
    this.filteredRegions = this.regions;
  }
}
