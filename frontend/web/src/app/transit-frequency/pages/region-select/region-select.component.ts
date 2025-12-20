import { ChangeDetectionStrategy, Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { Router } from '@angular/router';
import { RegionService } from '../../../feeds/services/region.service';
import { MetropolitanRegion } from '../../../feeds/models/region.models';
import { BrandCardComponent } from '../../../shared/components/brand-card.component';
import { BrandButtonComponent } from '../../../shared/components/brand-button.component';

@Component({
  selector: 'app-region-select',
  standalone: true,
  imports: [CommonModule, BrandCardComponent, BrandButtonComponent],
  template: `
    <app-brand-card title="Select a Region" subtitle="Showing regions with at least one imported feed">
      <div class="regions" role="list">
        @for (region of regions; track region.regionOnestopId) {
          <div class="region" role="listitem">
            <div class="info">
              <div class="name">{{ region.name }}</div>
              <small>{{ region.adm0Name }} {{ region.adm1Name }}</small>
              <small>Feeds: {{ region.feedCount }}</small>
            </div>
            <app-brand-button variant="primary" (click)="select(region.regionOnestopId)">
              View region
            </app-brand-button>
          </div>
        }
      </div>
    </app-brand-card>
  `,
  styles: [`
    .regions { display: flex; flex-direction: column; gap: 12px; }
    .region { display: flex; align-items: center; justify-content: space-between; padding: 8px 0; border-bottom: 1px solid var(--mat-sys-outline, #e2e8f0); }
    .info { display: flex; flex-direction: column; gap: 2px; color: var(--mat-sys-on-surface, #0f172a); }
    .name { font-weight: 700; }
    :host-context(.dark-theme) .region { border-bottom-color: rgba(148, 163, 184, 0.3); }
    :host-context(.dark-theme) .info { color: var(--mat-sys-on-surface, #e5e7eb); }
  `],
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class RegionSelectComponent implements OnInit {
  regions: MetropolitanRegion[] = [];

  constructor(
    private readonly regionService: RegionService,
    private readonly router: Router
  ) {}

  ngOnInit(): void {
    this.regionService.listRegions().subscribe(regions => {
      this.regions = regions.filter(region => region.feedCount > 0);
    });
  }

  select(regionId: string): void {
    this.router.navigate(['/transit-frequency/regions', regionId]);
  }
}
