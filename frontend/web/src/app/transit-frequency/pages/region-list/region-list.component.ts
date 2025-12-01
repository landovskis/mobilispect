import { Component, OnInit, ChangeDetectionStrategy } from '@angular/core';
import { CommonModule } from '@angular/common';
import { Router } from '@angular/router';
import { RegionService } from '../../../feeds/services/region.service';
import { MetropolitanRegion } from '../../../feeds/models/region.models';
import { BrandCardComponent } from '../../../shared/components/brand-card.component';
import { BrandButtonComponent } from '../../../shared/components/brand-button.component';

@Component({
  selector: 'app-region-list',
  standalone: true,
  imports: [CommonModule, BrandCardComponent, BrandButtonComponent],
  template: `
    <app-brand-card title="Regions" subtitle="All regions with imported feeds">
      <div class="grid" role="list">
        @for (region of regions; track region.regionOnestopId) {
          <div class="region-card" role="listitem">
            <div class="info">
              <div class="name">{{ region.name }}</div>
              <small>{{ region.adm0Name }} {{ region.adm1Name }}</small>
              <small>Feeds: {{ region.feedCount }}</small>
            </div>
            <app-brand-button variant="primary" (click)="goToRegion(region.regionOnestopId)">
              View
            </app-brand-button>
          </div>
        }
      </div>
    </app-brand-card>
  `,
  styles: [`
    .grid { display: flex; flex-direction: column; gap: 12px; }
    .region-card { display: flex; justify-content: space-between; align-items: center; padding: 12px; border: 1px solid var(--mat-sys-outline, #e2e8f0); border-radius: 12px; }
    .info { display: flex; flex-direction: column; gap: 2px; color: var(--mat-sys-on-surface, #0f172a); }
    .name { font-weight: 700; }
    :host-context(.dark-theme) .region-card { border-color: rgba(148, 163, 184, 0.3); }
    :host-context(.dark-theme) .info { color: var(--mat-sys-on-surface, #e5e7eb); }
  `],
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class RegionListComponent implements OnInit {
  regions: MetropolitanRegion[] = [];

  constructor(
    private readonly regionService: RegionService,
    private readonly router: Router
  ) {}

  ngOnInit(): void {
    this.regionService.listRegions().subscribe(regions => {
      this.regions = regions;
    });
  }

  goToRegion(regionId: string): void {
    this.router.navigate(['/regions', regionId]);
  }
}
